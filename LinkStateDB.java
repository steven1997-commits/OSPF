import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

public class LinkStateDB {

    // a router should map one of these linkstateDBs to each
    // router in the network - including itself
    // each link state DB will map from a router to its incoming links
    // and their costs
    private Map<String, HashMap<String, Integer>> DB;
    private Map<String, Integer> LinkCost = new HashMap<>();
    CustomLogger logger;

    public LinkStateDB(CustomLogger logger) {
        this.DB = new HashMap<String, HashMap<String, Integer>>();
        this.logger = logger;
    }

    public void update(int id, HashMap<String, Integer> links) {
        String neigh_id = Integer.toUnsignedString(id);
        if (this.DB.containsKey(neigh_id)) {
            HashMap<String, Integer> currLinks = this.DB.get(neigh_id);
            Set<String> keys = links.keySet();
            for (String key : keys) {
                currLinks.put(key,links.get(key));
                this.LinkCost.put(key,links.get(key));
            }
            this.DB.put(neigh_id,currLinks);
        } else {
            this.DB.put(neigh_id,links);
            for (String key : links.keySet()) {
                this.LinkCost.put(key,links.get(key));
            }
        }
    }

    public void printDB() {
        //System.out.println("size: " + this.DB.size());
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
        //System.out.println("size: " + this.DB.size());
        Set<String> keys = this.DB.keySet();
        for (String key : keys) {
            this.logger.log("From " + key + ":");
            HashMap<String, Integer> linkdb = this.DB.get(key);
            Set<String> links = linkdb.keySet();
            for (String link : links) {
                this.logger.log("Link: " + link + " , Cost: " + linkdb.get(link));
            }
        }
    }

    public int getCost(int r_id, int l_id) {
        String router_id = Integer.toUnsignedString(r_id);
        String link_id = Integer.toUnsignedString(l_id);
        if (this.DB.containsKey(router_id)) {
            HashMap<String, Integer> links = this.DB.get(router_id);
            if (links.containsKey(link_id)) {
                return links.get(link_id);
            }
        }
        return -1;
    }

    public int getCost(String router_id, int l_id) {
        String link_id = Integer.toUnsignedString(l_id);
        if (this.DB.containsKey(router_id)) {
            HashMap<String, Integer> links = this.DB.get(router_id);
            if (links.containsKey(link_id)) {
                return links.get(link_id);
            }
        }
        return -1;
    }

    public int getCost(String router_id, String link_id) {
        if (this.DB.containsKey(router_id)) {
            HashMap<String, Integer> links = this.DB.get(router_id);
            if (links.containsKey(link_id)) {
                return links.get(link_id);
            }
        }
        return -1;
    }

    public int getCost(int l_id) {
        String link_id = Integer.toUnsignedString(l_id);
        if (this.LinkCost.containsKey(link_id)) {
            return this.LinkCost.get(link_id);
        }
        return -1;
    }

    //get the links for a router
    public HashSet<String> getLinks(String r) {
        Set<String> links = this.DB.get(r).keySet();
        return new HashSet<String>(links);
    }

    public Set<String> getRouters() {
        return this.DB.keySet();
    }

}