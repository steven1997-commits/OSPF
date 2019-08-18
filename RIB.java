import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

public class RIB {

    private HashMap<String, HashMap<String, Integer>> DB = new HashMap<String, HashMap<String, Integer>>();
    private HashMap<String, HashSet<String>> InNodes = new HashMap<String,HashSet<String>>();
    private HashMap<String, HashSet<String>> OutNodes = new HashMap<String,HashSet<String>>();
    private CustomLogger logger;
    private String src;

    public void update(int id, HashMap<String, Integer> links) {
        String neigh_id = Integer.toUnsignedString(id);
        if (this.DB.containsKey(neigh_id)) {
            HashMap<String, Integer> currLinks = this.DB.get(neigh_id);
            Set<String> keys = links.keySet();
            for (String key : keys) {
                currLinks.put(key,links.get(key));
            }
            this.DB.put(neigh_id,currLinks);
        } else {
            this.DB.put(neigh_id,links);
        }
    }

    public void printDB() {
        Set<String> keys = this.DB.keySet();
        for (String key : keys) {
            System.out.println("From " + key + ":");
            HashMap<String, Integer> linkdb = this.DB.get(key);
            Set<String> links = linkdb.keySet();
            for (String link : links) {
                System.out.println("Link: " + link + " , Cost: " + linkdb.get(link));
            }
        }
    }

    public void logDB() {
        this.logger.log("Logging RIB...");
        Set<String> keys = this.DB.keySet();
        for (String key : keys) {
            HashMap<String, Integer> linkdb = this.DB.get(key);
            Set<String> links = linkdb.keySet();
            for (String link : links) {
                this.logger.log("Destination: " + key + " Link: " + link + " , Cost: " + linkdb.get(link));
            }
        }
    }

    public void initialize(LinkStateDB lsdb, int source, CustomLogger logger) {
        this.logger = logger;
        this.src = Integer.toUnsignedString(source);
        this.DB.clear();
        this.InNodes.clear();
        this.OutNodes.clear();
        String src_id = Integer.toUnsignedString(source);
        Set<String> routers = lsdb.getRouters();
        for (String r : routers) {
            HashMap<String, Integer> linkcost = new HashMap<>();
            if (Integer.parseUnsignedInt(r) == source) {
                linkcost.put("Local",0);
                this.DB.put(r,linkcost);
                HashSet<String> src_links = lsdb.getLinks(src_id);
                this.InNodes.put(src_id,src_links);
            } else {
                linkcost.put("Unknown",99999999);
                this.DB.put(r,linkcost);
                //if not the source node, put in the outset
                this.OutNodes.put(r,new HashSet<String>(lsdb.getLinks(r)));
            }
        }
        //put local router into the in-set, rest in out-set
    }

    /* Retrieves all nodes (and their links) that are adjacent to the inNodes */
    private HashMap<String, HashMap<String, String>> getAdjacent() {
        /* Order: OutRouter that is adjacent, InRouter its ajdacent to, name of link connecting them */
        HashMap<String, HashMap<String,String>> adj = new HashMap<String, HashMap<String,String>>();
        for (String inRouter : this.InNodes.keySet()) {
            for (String outRouter : this.OutNodes.keySet()) {
                HashMap<String,String> adjLinks = new HashMap<>();
                if (adj.containsKey(outRouter)) {
                    adjLinks = adj.get(outRouter);
                }
                Set<String> links = this.OutNodes.get(outRouter);
                for (String link : links) {
                    //if the in-router has the link too, then they are adjacent
                    if (this.InNodes.get(inRouter).contains(link)) {
                        adjLinks.put(inRouter,link);
                    }
                }
                if (adjLinks.size() > 0) {
                    adj.put(outRouter,adjLinks);
                }
            }
        }
        return adj;
    }

    private int getCost(String inRouter) {
        for (String link : this.DB.get(inRouter).keySet()) {
            return this.DB.get(inRouter).get(link);
        }
        return 9999999;
    }

    /* Run the alg, find the min cost, the node it is connected to, and add to it */
    public void runOSPF(LinkStateDB db) {
        System.out.println("Running OSPF...");
        while(this.OutNodes.size() > 0) {
            HashMap<String, HashMap<String,String>> adj = this.getAdjacent();
            HashMap<String, HashMap<String,Integer>> costs = new HashMap<String, HashMap<String, Integer>>();
            if (adj.size() == 0) {
                break;
            }
            for (String adjRouter : adj.keySet()) {
                //for every in-router that it is adjacent to:
                for (String inRouter : adj.get(adjRouter).keySet()) {
                    //get the cost of the in-router and the link it connects to adjRouter with
                    int cost = db.getCost(inRouter, adj.get(adjRouter).get(inRouter));
                    for (int currCost : this.DB.get(adjRouter).values()) {
                        int newCost = this.getCost(inRouter) + cost;
                        if (newCost < currCost) {
                            HashMap<String, Integer> linkcost = new HashMap<>();
                            String link = this.getLink(inRouter, adjRouter);
                            linkcost.put(link,newCost);
                            if (costs.containsKey(adjRouter)) {
                                int adjcost = this.getAdjCost(costs, adjRouter);
                                if (adjcost > newCost) {
                                    costs.put(adjRouter,linkcost);
                                }
                            } else if (!costs.containsKey(adjRouter)) {
                                costs.put(adjRouter,linkcost);
                            }
                        }
                    }
                }
            }
            int min = 9999999;
            String router = "";
            String link = "";
            for (String r : costs.keySet()) {
                HashMap<String, Integer> r_costs = costs.get(r);
                for (String l : r_costs.keySet()) {
                    int c = costs.get(r).get(l);
                    if (c < min) {
                        min = c;
                        router = r;
                        link = l;
                    }
                }
            }
            HashMap<String, Integer> link_cost = new HashMap<>();
            link_cost.put(link,min);
            this.DB.put(router,link_cost);
            this.InNodes.put(router, this.OutNodes.get(router));
            this.OutNodes.remove(router);
        }
    }

    private String getLink(String router, String adj) {
        String l = "";
        for (String link : this.DB.get(router).keySet()) {
            l = link;
        }
        if (l.equalsIgnoreCase("Local")) {
            for (String links : this.OutNodes.get(adj)) {
                if (this.InNodes.get(router).contains(links)) {
                    l = links;
                    break;
                }
            }            
        }
        return l;
    }

    public void printInRouters() {
        System.out.println("Printing in-routers....");
        for (String router : this.InNodes.keySet()) {
            for (String link : this.InNodes.get(router)) {
                System.out.println("In-Router: " + router + " Link: " + link);
            }
        }
    }

    public void printOutRouters() {
        System.out.println("Printing out-routers....");
        for (String router : this.OutNodes.keySet()) {
            for (String link : this.OutNodes.get(router)) {
                System.out.println("Out-Router: " + router + " Link: " + link);
            }
        }
    }

    public void printAdj(HashMap<String, HashMap<String,String>> adj) {
        System.out.println("Printing adjacents...");
        for (String outRouter : adj.keySet()) {
            for (String inRouter : adj.get(outRouter).keySet()) {
                String link = adj.get(outRouter).get(inRouter);
                System.out.println("Adjacent router: " + outRouter + " In: " + inRouter + " Link: " + link);
            }
        }
    }

    public void printRIB() {
        System.out.println("Printing RIB...");
        for (String dest : this.DB.keySet()) {
            for (String link : this.DB.get(dest).keySet()) {
                System.out.println("Destination: " + dest + " Link: " + link + " Cost: " + this.DB.get(dest).get(link));
            }
        }
    }

    private int getAdjCost(HashMap<String, HashMap<String,Integer>> costs, String router) {
        for (String link : costs.get(router).keySet()) {
            return costs.get(router).get(link);
        }
        return -1;
    }

}