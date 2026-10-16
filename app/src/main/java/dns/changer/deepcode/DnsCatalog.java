package dns.changer.deepcode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DnsCatalog {
    private DnsCatalog() {}

    public static List<DnsServer> builtins(DnsProtocol protocol) {
        List<DnsServer> list = new ArrayList<>();
        switch (protocol) {
            case TCP:
                addTcp(list);
                break;
            case DOT:
                addDot(list);
                break;
            case DOH:
                addDoh(list);
                break;
            case UDP:
            default:
                addUdp(list);
                break;
        }
        return list;
    }

    private static void addUdp(List<DnsServer> list) {
        for (Group g : UNIQUE_GROUPS) {
            DnsServer server = udp(g.name, g.a, g.b);
            server.setSearchTags(tagsOf(g));
            list.add(server);
        }
    }

    private static void addTcp(List<DnsServer> list) {
        for (Group g : UNIQUE_GROUPS) {
            DnsServer server = tcp(g.name, g.a, g.b);
            server.setSearchTags(tagsOf(g));
            list.add(server);
        }
    }

    // Encrypted DNS: only Iran-reachable providers.
    private static void addDot(List<DnsServer> list) {
        list.add(dot("Vanilla", "194.146.68.68", "194.146.68.66", "dns.vanillapp.ir", 853));
        // No hardcoded bootstrap IP: that IP was found to be serving an expired certificate
        // (a stale/decommissioned backend), so the hostname is resolved live instead.
        list.add(dot("Shecan", "", "", "free.shecan.ir", 853));
    }

    private static void addDoh(List<DnsServer> list) {
        list.add(doh("Vanilla", "194.146.68.68", "194.146.68.66", "https://dns.vanillapp.ir/dns-query"));
        list.add(doh("Shecan", "", "", "https://free.shecan.ir/dns-query"));
    }

    private static DnsServer udp(String name, String a, String b) {
        return new DnsServer(name, a, b, "", "", "UDP", "", "", 53);
    }

    private static DnsServer tcp(String name, String a, String b) {
        return new DnsServer(name, a, b, "", "", "TCP", "", "", 53);
    }

    private static DnsServer dot(String name, String a, String b, String host, int port) {
        return new DnsServer(name, a, b, "", "", "DOT", host, "", port);
    }

    private static DnsServer doh(String name, String a, String b, String url) {
        return new DnsServer(name, a, b, "", "", "DOH", "", url, 443);
    }

    private static final class GamePair {
        final String game;
        final String label;
        final String a;
        final String b;
        GamePair(String game, String label, String a, String b) {
            this.game = game;
            this.label = label;
            this.a = a;
            this.b = b;
        }
    }

    private static GamePair[] numbered(String game, String[][] pairs) {
        GamePair[] out = new GamePair[pairs.length];
        for (int i = 0; i < pairs.length; i++) {
            String label = pairs.length > 1 ? game + " #" + (i + 1) : game;
            out[i] = new GamePair(game, label, pairs[i][0], pairs[i][1]);
        }
        return out;
    }

    private static final List<GamePair> GAME_PAIRS = buildGamePairs();

    /** One unique DNS pair (order-insensitive) and every game that uses it. */
    private static final class Group {
        final String a;
        final String b;
        final List<String> games = new ArrayList<>();
        String name;
        Group(String a, String b) {
            this.a = a;
            this.b = b;
        }
    }

    private static final List<Group> UNIQUE_GROUPS = buildUniqueGroups();

    private static List<Group> buildUniqueGroups() {
        Map<String, Group> map = new LinkedHashMap<>();
        for (GamePair p : GAME_PAIRS) {
            String key = p.a.compareTo(p.b) <= 0 ? p.a + "|" + p.b : p.b + "|" + p.a;
            Group g = map.get(key);
            if (g == null) {
                g = new Group(p.a, p.b);
                map.put(key, g);
            }
            if (!g.games.contains(p.game)) g.games.add(p.game);
        }

        List<Group> groups = new ArrayList<>(map.values());
        Map<String, Integer> nameCount = new LinkedHashMap<>();
        for (Group g : groups) {
            g.name = displayName(g);
            Integer c = nameCount.get(g.name);
            nameCount.put(g.name, c == null ? 1 : c + 1);
        }
        // Same name on several different pairs (e.g. three adult-block resolvers): number them.
        Map<String, Integer> seen = new LinkedHashMap<>();
        for (Group g : groups) {
            if (nameCount.get(g.name) > 1) {
                Integer n = seen.get(g.name);
                n = n == null ? 1 : n + 1;
                seen.put(g.name, n);
                g.name = g.name + " #" + n;
            }
        }
        return groups;
    }

    private static String tagsOf(Group g) {
        StringBuilder sb = new StringBuilder();
        for (String game : g.games) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(game);
        }
        return sb.toString();
    }

    private static String displayName(Group g) {
        if (g.games.size() == 1) return g.games.get(0);
        int shown = Math.min(3, g.games.size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < shown; i++) {
            if (i > 0) sb.append(" · ");
            sb.append(g.games.get(i));
        }
        if (g.games.size() > shown) sb.append(" +").append(g.games.size() - shown);
        return sb.toString();
    }

    private static List<GamePair> buildGamePairs() {
        List<GamePair> out = new ArrayList<>();

        addAll(out, numbered("FC 25", new String[][]{
                {"10.202.10.10", "10.202.10.11"},
                {"78.157.42.100", "78.157.42.101"},
        }));
        addAll(out, numbered("Life After", new String[][]{
                {"178.22.122.100", "185.51.200.2"},
        }));
        addAll(out, numbered("Fortnite", new String[][]{
                {"178.22.122.100", "185.51.200.2"},
        }));
        addAll(out, numbered("eFootball", new String[][]{
                {"10.202.10.10", "10.202.10.11"},
                {"78.157.42.100", "78.157.42.101"},
                {"178.22.122.100", "185.51.200.2"},
        }));
        addAll(out, numbered("Standoff 2", new String[][]{
                {"185.51.200.2", "178.22.122.100"},
        }));
        addAll(out, numbered("Metalstorm", new String[][]{
                {"178.22.122.100", "185.51.200.2"},
        }));
        addAll(out, numbered("Brawl Stars", new String[][]{
                {"10.202.10.10", "10.202.10.11"},
                {"178.22.122.100", "185.51.200.2"},
                {"78.157.42.100", "78.157.42.101"},
        }));
        addAll(out, numbered("Hay Day", new String[][]{
                {"178.22.122.100", "185.51.200.2"},
        }));
        addAll(out, numbered("Clash of Clans", new String[][]{
                {"76.76.2.11", "76.76.10.11"},
                {"76.76.2.22", "76.76.10.22"},
                {"10.202.10.10", "10.202.10.11"},
                {"78.157.42.100", "78.157.42.101"},
                {"178.22.122.100", "10.202.10.10"},
        }));
        addAll(out, numbered("PUBG", new String[][]{
                {"76.76.2.11", "76.76.10.11"},
                {"76.76.2.22", "76.76.10.22"},
                {"10.202.10.10", "10.202.10.11"},
                {"178.22.122.100", "76.76.2.22"},
                {"78.157.42.100", "78.157.42.101"},
                {"178.22.122.100", "185.51.200.2"},
        }));
        addAll(out, numbered("Arena Breakout", new String[][]{
                {"178.22.122.100", "185.51.200.2"},
                {"10.202.10.10", "10.202.10.11"},
                {"78.157.42.100", "78.157.42.101"},
        }));
        addAll(out, numbered("NBA 2K", new String[][]{
                {"10.202.10.10", "10.202.10.11"},
                {"178.22.122.100", "185.51.200.2"},
        }));
        addAll(out, numbered("Boom Beach", new String[][]{
                {"10.202.10.10", "10.202.10.11"},
                {"78.157.42.100", "78.157.42.101"},
                {"178.22.122.100", "185.51.200.2"},
        }));
        addAll(out, numbered("Among Us", new String[][]{
                {"10.202.10.10", "10.202.10.11"},
                {"185.51.200.2", "178.22.122.100"},
        }));
        addAll(out, numbered("Free Fire", new String[][]{
                {"78.157.42.100", "10.202.10.10"},
        }));
        addAll(out, numbered("Ad Block", new String[][]{
                {"94.140.14.14", "94.140.15.15"},
        }));
        addAll(out, numbered("Mobile Legends", new String[][]{
                {"10.202.10.10", "10.202.10.11"},
                {"78.157.42.100", "78.157.42.101"},
                {"178.22.122.100", "185.51.200.2"},
                {"76.76.2.22", "76.76.10.22"},
        }));
        addAll(out, numbered("Adult Content Block", new String[][]{
                {"1.1.1.3", "1.0.0.3"},
                {"185.228.168.168", "185.228.169.168"},
                {"208.67.222.222", "208.67.220.220"},
        }));
        addAll(out, numbered("Marvel Games", new String[][]{
                {"178.22.122.100", "185.51.200.2"},
                {"10.202.10.10", "10.202.10.11"},
                {"78.157.42.100", "78.157.42.101"},
        }));
        addAll(out, numbered("Modern Ops", new String[][]{
                {"178.22.122.100", "185.51.200.2"},
                {"10.202.10.10", "10.202.10.11"},
        }));
        addAll(out, numbered("COD Warzone", new String[][]{
                {"76.76.2.22", "76.76.10.22"},
        }));
        addAll(out, numbered("Zooba", new String[][]{
                {"10.202.10.10", "10.202.10.11"},
        }));
        addAll(out, numbered("Delta Force", new String[][]{
                {"5.250.252.150", "78.157.42.100"},
                {"10.202.10.10", "10.202.10.11"},
                {"185.51.200.2", "178.22.122.100"},
        }));
        addAll(out, numbered("League of Legends", new String[][]{
                {"10.202.10.10", "10.202.10.11"},
                {"178.22.122.100", "185.51.200.2"},
        }));
        addAll(out, numbered("Other Games", new String[][]{
                {"178.22.122.100", "185.51.200.2"},
                {"10.202.10.10", "10.202.10.11"},
                {"78.157.42.100", "78.157.42.101"},
        }));
        addAll(out, numbered("Squad Busters", new String[][]{
                {"10.202.10.10", "78.157.42.100"},
                {"178.22.122.100", "185.51.200.2"},
        }));
        addAll(out, numbered("Castle Clash", new String[][]{
                {"78.157.42.100", "78.157.42.101"},
                {"78.157.42.100", "10.202.10.10"},
                {"10.202.10.10", "10.202.10.11"},
                {"178.22.122.100", "185.51.200.2"},
        }));
        addAll(out, numbered("Malware Block", new String[][]{
                {"1.1.1.2", "1.0.0.2"},
        }));
        addAll(out, numbered("Call of Duty", new String[][]{
                {"76.76.2.11", "76.76.10.11"},
                {"76.76.2.22", "76.76.10.22"},
                {"10.202.10.10", "10.202.10.11"},
                {"10.202.10.10", "78.157.42.100"},
                {"10.202.10.10", "76.76.2.22"},
                {"10.202.10.10", "178.22.122.100"},
                {"78.157.42.100", "78.157.42.101"},
                {"178.22.122.100", "185.51.200.2"},
        }));
        addAll(out, numbered("Clash Royale", new String[][]{
                {"76.76.2.22", "76.76.10.22"},
                {"76.76.2.11", "76.76.10.11"},
                {"10.202.10.10", "10.202.10.11"},
                {"178.22.122.100", "185.51.200.2"},
        }));

        return out;
    }

    private static void addAll(List<GamePair> out, GamePair[] pairs) {
        for (GamePair p : pairs) out.add(p);
    }
}
