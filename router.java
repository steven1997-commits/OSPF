import java.io.*;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.lang.Thread;
import java.lang.Integer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.net.InetAddress;

import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

public class router extends Thread {

    //router number fixed to 5 for simplicity
    private static final int NBR_ROUTER = 5;

    //Link State DB - map from router id to a database of
    //incoming router numbers, links and costs
    // - includes this router itself
    private LinkStateDB LSDB;
    private RIB rib = new RIB();

    //socket
    DatagramSocket outSocket;

    //logger
    CustomLogger logger;

    //port and hostname data
    int ROUTER_ID;
    InetAddress NSE_HOST;
    int NSE_PORT;
    int ROUTER_PORT;

    //sizes of below packet/DB classes
    static final int pkt_HELLO_SIZE = 8;
    static final int pkt_LSPDU_SIZE = 20;
    static final int pkt_INIT_SIZE = 4;
    static final int link_cost_SIZE = 8;
    static final int circuit_DB_SIZE = 4 + link_cost_SIZE * router.NBR_ROUTER;

    //abstract pkt class, provide serialize and deserialize methods for below classes
    abstract class pkt {
        abstract public byte[] getBytes();
    }

    /* packet and circuit DB structs */
    class pkt_HELLO extends pkt {
        /* These integers are all unsigned */
        public int router_id; //id of router sending the HELLO pkt
        public int link_id; //id of the link through which it was sent

        public pkt_HELLO(int id, int link) {
            this.router_id = id;
            this.link_id = link;
        }

        @Override
        public byte[] getBytes() {
            ByteBuffer b = ByteBuffer.allocate(router.pkt_HELLO_SIZE);
            b.order(ByteOrder.LITTLE_ENDIAN);
            b.putInt(this.router_id);
            b.putInt(this.link_id);
            byte arr[] = b.array();
            return arr;
        }
    }
    
    class pkt_LSPDU extends pkt {
        /* These integers are all unsigned */
        public int sender;
        public int router_id;
        public int link_id;
        public int link_cost;
        public int via;

        public pkt_LSPDU(int s, int ri, int li, int lc, int via) {
            this.sender = s;
            this.router_id = ri;
            this.link_id = li;
            this.link_cost = lc;
            this.via = via;
        }

        @Override
        public byte[] getBytes() {
            ByteBuffer b = ByteBuffer.allocate(router.pkt_LSPDU_SIZE);
            b.order(ByteOrder.LITTLE_ENDIAN);
            b.putInt(this.sender);
            b.putInt(this.router_id);
            b.putInt(this.link_id);
            b.putInt(this.link_cost);
            b.putInt(this.via);
            byte arr[] = b.array();
            return arr;
        }
    }
    
    class pkt_INIT extends pkt {
        /* These integers are all unsigned */
        public int router_id;

        public pkt_INIT(int id) {
            this.router_id = id;
        }

        @Override
        public byte[] getBytes() {
            ByteBuffer b = ByteBuffer.allocate(router.pkt_INIT_SIZE);
            b.order(ByteOrder.LITTLE_ENDIAN);
            b.putInt(this.router_id);
            byte arr[] = b.array();
            return arr;
        }
    }
    
    class link_cost extends pkt {
        /* These integers are all unsigned */
        public int link;
        public int cost;

        public link_cost(int l, int c) {
            this.link = l;
            this.cost = c;
        }

        @Override
        public byte[] getBytes() {
            ByteBuffer b = ByteBuffer.allocate(router.link_cost_SIZE);
            b.order(ByteOrder.LITTLE_ENDIAN);
            b.putInt(this.link);
            b.putInt(this.cost);
            byte arr[] = b.array();
            return arr;
        }
    }
    
    class circuit_DB extends pkt {
        /* These integers are all unsigned */

        //number of links attached to this router (at most NBR_ROUTER)
        public int nbr_link;
        public link_cost linkcost[] = new link_cost[router.NBR_ROUTER];

        public int getCost(int link) {
            for (int i = 0 ; i < nbr_link ; i++) {
                if (linkcost[i].link == link) {
                    return linkcost[i].cost;
                }
            }
            return -1;
        }

        @Override
        public byte[] getBytes() {
            ByteBuffer b = ByteBuffer.allocate(router.link_cost_SIZE);
            b.order(ByteOrder.LITTLE_ENDIAN);
            b.putInt(this.nbr_link);
            for (link_cost lc : this.linkcost) {
                b.putInt(lc.link);
                b.putInt(lc.cost);
            }
            byte arr[] = b.array();
            return arr;
        }
    }

    public router(int id, String host, int port, int rport) {
        this.ROUTER_ID = id;
        this.NSE_PORT = port;
        this.ROUTER_PORT = rport;
        try {
            this.NSE_HOST = InetAddress.getByName(host);;
            this.outSocket = new DatagramSocket(this.ROUTER_PORT);
        } catch(Exception e) {
            e.printStackTrace();
        }
        this.logger = new CustomLogger(this.getClass().getSimpleName() + Integer.toUnsignedString(this.ROUTER_ID));
        this.LSDB = new LinkStateDB(logger);
    }

    /* Convert a byte array into a DatagramPacket and send it */
    public void send(byte data[], int length) {
        try {
            DatagramPacket p = new DatagramPacket(data, length, this.NSE_HOST, this.NSE_PORT);
            this.outSocket.send(p);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public pkt_LSPDU procReceivedPDU(DatagramPacket p) {
        if (p.getLength() != this.pkt_LSPDU_SIZE) {
            return null;
        }
        byte data[] = p.getData();
        ByteBuffer bb = ByteBuffer.wrap(data);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        int sender = bb.getInt();
        int r_id = bb.getInt();
        int l_id = bb.getInt();
        int l_c = bb.getInt();
        int via = bb.getInt();
        pkt_LSPDU lspdu = new pkt_LSPDU(sender, r_id, l_id, l_c, via);
        return lspdu;
    }

    /* Process a received pkt_HELLO */
    public pkt_HELLO procReceivedHello(DatagramPacket p, circuit_DB db) {
        //CHECK TO SEE IF IT IS A HELLO PKT FIRST!!!
        if (p.getLength() != this.pkt_HELLO_SIZE) {
            return null;
        }
        byte data[] = p.getData();
        ByteBuffer bb = ByteBuffer.wrap(data);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        int id = bb.getInt();
        int link = bb.getInt();
        this.logger.log("Received pkt_HELLO router_id: " 
        + Integer.toUnsignedString(id)
        + " link_id: " 
        + Integer.toUnsignedString(link));
        pkt_HELLO hello = new pkt_HELLO(id, link);
        HashMap<String, Integer> linkcost = new HashMap<String, Integer>();
        linkcost.put(Integer.toUnsignedString(link),db.getCost(link));
        this.LSDB.update(id,linkcost);
        return hello;
    }

    /* Process a received circuit_DB packet */
    public circuit_DB procReceivedDB(DatagramPacket p) {
        //System.out.println("Offset: " + p.getOffset() + " ,Length: " + p.getLength());
        //start by copying out the byte data
        byte data[] = p.getData();
        circuit_DB db = new circuit_DB();
        //now process each array
        ByteBuffer bb = ByteBuffer.wrap(data);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        db.nbr_link = bb.getInt();
        this.logger.log("Received circuit_DB, number of links: " + Integer.toUnsignedString(db.nbr_link));
        for (int i = 0 ; i < db.nbr_link ; i++ ) {
            int link = bb.getInt();
            int cost = bb.getInt();
            this.logger.log("circuit_DB link: " + Integer.toUnsignedString(link) + " cost: " + Integer.toUnsignedString(cost));
            db.linkcost[i] = new link_cost(link,cost);
            HashMap<String, Integer> linkc = new HashMap<>();
            linkc.put(Integer.toUnsignedString(link),cost);
            this.LSDB.update(this.ROUTER_ID, linkc);
        }
        return db;
    }

    private void printLSDB() {
        System.out.println("-----------------");
        System.out.println("Host router: " + Integer.toUnsignedString(this.ROUTER_ID));
        this.LSDB.printDB();
        System.out.println("-----------------");
    }

    private void logLSDB() {
        this.logger.log("Logging topology database for router " + Integer.toUnsignedString(this.ROUTER_ID) + ":");
        //this.logger.log("Host router: " + Integer.toUnsignedString(this.ROUTER_ID));
        this.LSDB.logDB();
    }

    private Set<String> getNeighbours(circuit_DB db) {
        Set<String> routers = this.LSDB.getRouters();
        Set<String> neighbours = new HashSet<String>();
        for (String r : routers) {
            for (int i = 0 ; i < db.nbr_link ; i++) {
                int res = this.LSDB.getCost(r,db.linkcost[i].link);
                if (res != -1) {
                    neighbours.add(r);
                }
            }

        }
        return neighbours;
    }

    private int getNeighbourLink(String n, circuit_DB db) {
        for (link_cost lc : db.linkcost) {
            if (this.LSDB.getCost(n, lc.link) != -1) {
                return lc.link;
            }
        }
        return -1;
    }

    /* Assume everything has been initialized */
    public void run() {
        //first, send the init packet to the other routers (NSE)
        System.out.println("Running...");
        pkt_INIT init = new pkt_INIT(this.ROUTER_ID);
        this.send(init.getBytes(), this.pkt_INIT_SIZE);
        this.logger.log("Sent pkt_INIT with ID: " + Integer.toUnsignedString(this.ROUTER_ID));

        try {
            byte buf[] = new byte[1000];
            DatagramPacket dp = new DatagramPacket(buf, buf.length);
            this.outSocket.receive(dp);
            circuit_DB db = this.procReceivedDB(dp);
            this.logLSDB();

            //send a HELLO to each link in circuit_DB, also remember links
            for (int i = 0 ; i < db.nbr_link ; i++) {
                pkt_HELLO hello = new pkt_HELLO(this.ROUTER_ID, db.linkcost[i].cost);
                byte data[] = hello.getBytes();
                //log the sent hellos
                this.send(data, this.pkt_HELLO_SIZE);
                this.logger.log("Sent HELLO with router ID: " 
                + Integer.toUnsignedString(hello.router_id) 
                + " Link ID: " 
                + Integer.toUnsignedString(hello.link_id));
            }

            //receive hellos and send LS PDUs and run alg.
            while (true) {
                //packet could be HELLO or LS PDU
                byte input[] = new byte[1000];
                DatagramPacket hp = new DatagramPacket(buf, buf.length);
                this.outSocket.receive(hp);
                pkt_HELLO hello = this.procReceivedHello(hp,db);
                if (hello != null) {
                    this.logLSDB();
                    //send set of LS_PDUs to each neighbour
                    //first filter out the neighbours from all routers
                    Set<String> neighbours = this.getNeighbours(db);
                    for (String neighbour : neighbours) {
                        Set<String> routers = this.LSDB.getRouters();
                        //for each router in our db, send all link info
                        for (String router : routers) {
                            if (router == neighbour) {
                                continue;
                            }
                            Set<String> links = this.LSDB.getLinks(router);
                            for (String link : links) {
                                int link_i = Integer.parseUnsignedInt(link);
                                int router_i = Integer.parseUnsignedInt(router);
                                int cost = this.LSDB.getCost(router_i, link_i);
                                pkt_LSPDU lspdu = new pkt_LSPDU(this.ROUTER_ID,
                                    router_i,
                                    link_i,
                                    cost,
                                    this.getNeighbourLink(neighbour, db));
                                this.send(lspdu.getBytes(),this.pkt_LSPDU_SIZE);
                                this.logger.log("Sent LSPDU"
                                + " sender: " + lspdu.sender
                                + " router_id: " + lspdu.router_id
                                + " link_id: " + lspdu.link_id
                                + " link_cost " + lspdu.link_cost
                                + " via: " + lspdu.via);
                            }
                        }
                    }
                    continue;
                }
                //if not hello, process it as an LS_PDU
                pkt_LSPDU lspdu = this.procReceivedPDU(hp);
                if (lspdu != null) {
                    this.logger.log("Received LSPDU"
                    + " sender: " + lspdu.sender
                    + " router_id: " + lspdu.router_id
                    + " link_id: " + lspdu.link_id
                    + " link_cost " + lspdu.link_cost
                    + " via: " + lspdu.via);
                    //only forward LSPDU if not a duplicate
                    if (this.LSDB.getCost(lspdu.router_id,lspdu.link_id) == -1) {
                        //if not a duplicate, add to database
                        HashMap<String, Integer> l_c = new HashMap<String, Integer>();
                        l_c.put(Integer.toUnsignedString(lspdu.link_id),lspdu.link_cost);
                        this.LSDB.update(lspdu.router_id, l_c);
                        //forward to all neighbours (get neighbour links via circuit_db)
                        Set<String> neighbours = this.getNeighbours(db);
                        for (String n : neighbours) {
                            pkt_LSPDU ls_pdu = new pkt_LSPDU(
                                this.ROUTER_ID, 
                                lspdu.router_id, 
                                lspdu.link_id, 
                                lspdu.link_cost, 
                                this.getNeighbourLink(n, db));
                            this.send(ls_pdu.getBytes(),this.pkt_LSPDU_SIZE);
                        }
                        this.rib.initialize(this.LSDB, this.ROUTER_ID, this.logger);
                        this.rib.runOSPF(this.LSDB);
                        this.logLSDB();
                        this.rib.logDB();
                    }
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Too few arguments, usage: router <router_id> <nse_host> <nse_port> <router_port>");
            System.exit(1);
        }
        router r = new router(
            Integer.parseInt(args[0]),
            args[1],
            Integer.parseInt(args[2]),
            Integer.parseInt(args[3])
        );
        r.run();
    }

}