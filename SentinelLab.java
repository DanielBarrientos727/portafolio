import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * SENTINELLAB - Infrastructure Operations Console (v1.0)
 * ------------------------------------------------------
 * Consola de monitoreo, diagnostico y simulacion de infraestructura,
 * 100% CLI (texto), tema Fedora / azul, un unico archivo, sin
 * dependencias externas: solo biblioteca estandar de Java (SE 9+).
 *
 * Compilar: javac SentinelLab.java
 * Ejecutar: java SentinelLab
 */
public class SentinelLab {

    // =====================================================================
    // UTILS
    // =====================================================================

    /** Codigos ANSI - paleta azul/Fedora. */
    static final class Ansi {
        static final String RESET = "\u001B[0m";
        static final String BOLD = "\u001B[1m";
        static final String DIM = "\u001B[2m";

        static final String BLUE = "\u001B[38;5;33m";
        static final String LIGHT_BLUE = "\u001B[38;5;39m";
        static final String DARK_BLUE = "\u001B[38;5;25m";
        static final String STEEL_BLUE = "\u001B[38;5;67m";

        static final String WHITE = "\u001B[97m";
        static final String GRAY = "\u001B[38;5;245m";
        static final String DARK_GRAY = "\u001B[38;5;238m";

        static final String GREEN = "\u001B[38;5;42m";
        static final String YELLOW = "\u001B[38;5;220m";
        static final String RED = "\u001B[38;5;196m";

        static final String BG_BLUE = "\u001B[48;5;24m";
    }

    /** Utilidades de terminal: limpieza, pausas, relleno de texto. */
    static final class Term {
        static void clear() {
            System.out.print("\u001B[H\u001B[2J\u001B[3J");
            System.out.flush();
        }

        static void sleep(long ms) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        static String repeat(String s, int n) {
            if (n <= 0) return "";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < n; i++) sb.append(s);
            return sb.toString();
        }

        static String pad(String s, int width) {
            if (s.length() >= width) return s.substring(0, width);
            return s + repeat(" ", width - s.length());
        }
    }

    // =====================================================================
    // MODELS
    // =====================================================================

    enum Health { HEALTHY, WARNING, CRITICAL }
    enum LogLevel { INFO, WARN, ERROR, CRITICAL }
    enum ProcState { RUNNING, SLEEPING, STOPPED, ZOMBIE }
    enum NodeStatus { ONLINE, DEGRADED, OFFLINE }
    enum Severity { LOW, MEDIUM, HIGH, CRITICAL }
    enum IncStatus { OPEN, INVESTIGATING, RESOLVED }
    enum Origin { REAL, SIM }
    enum Scenario { NORMAL, CPU_SPIKE, MEMORY_PRESSURE, NET_DEGRADED, PACKET_LOSS, NODE_DOWN, RECOVERY }

    static final class Snapshot {
        Instant time;
        double cpuPercent; Origin cpuOrigin;
        double ramUsedMB, ramTotalMB; Origin ramOrigin;
        double diskUsedGB, diskTotalGB; Origin diskOrigin = Origin.SIM;
        double netInKbps, netOutKbps; Origin netOrigin;
        double latencyMs; Origin latencyOrigin;
        long uptimeSeconds;
        int processCount;
        Health health;
    }

    static final class Proc {
        long pid; String name; double cpuPercent; double ramMB; ProcState state; int priority; Origin origin;
    }

    static final class Iface {
        String name; String ip; boolean up;
        double rxKbps, txKbps; Origin trafficOrigin;
        double latencyMs; Origin latencyOrigin = Origin.SIM;
        long packetsRx, packetsTx; Origin packetsOrigin;
        double lossPercent; Origin lossOrigin = Origin.SIM;
    }

    static final class LogEntry {
        Instant time; LogLevel level; String message;
        LogEntry(LogLevel l, String m) { time = Instant.now(); level = l; message = m; }
    }

    static final class Incident {
        String id; Severity severity; Instant time; String component; String description; IncStatus status;
    }

    static final class Node {
        String name; String type; NodeStatus status = NodeStatus.ONLINE;
        List<String> children = new ArrayList<>();
    }

    // =====================================================================
    // SYSTEM READER (datos reales via biblioteca estandar; Linux-aware)
    // =====================================================================

    static final class SysReader {
        static final boolean LINUX = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT).contains("linux");

        /** /proc/stat -> {jiffiesOcupados, jiffiesTotales} o null si no aplica. */
        static long[] readCpuJiffies() {
            if (!LINUX) return null;
            try (BufferedReader r = new BufferedReader(new java.io.FileReader("/proc/stat"))) {
                String line = r.readLine();
                if (line == null || !line.startsWith("cpu ")) return null;
                String[] p = line.trim().split("\\s+");
                long user = Long.parseLong(p[1]);
                long nice = Long.parseLong(p[2]);
                long system = Long.parseLong(p[3]);
                long idle = Long.parseLong(p[4]);
                long iowait = Long.parseLong(p[5]);
                long irq = Long.parseLong(p[6]);
                long softirq = Long.parseLong(p[7]);
                long steal = p.length > 8 ? Long.parseLong(p[8]) : 0;
                long busy = user + nice + system + irq + softirq + steal;
                long total = busy + idle + iowait;
                return new long[]{busy, total};
            } catch (Exception e) {
                return null;
            }
        }

        /** /proc/meminfo -> {usadaMB, totalMB} o null. */
        static double[] readMemInfo() {
            if (!LINUX) return null;
            try (BufferedReader r = new BufferedReader(new java.io.FileReader("/proc/meminfo"))) {
                long totalKb = -1, availKb = -1, freeKb = -1;
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.startsWith("MemTotal:")) totalKb = extractKb(line);
                    else if (line.startsWith("MemAvailable:")) availKb = extractKb(line);
                    else if (line.startsWith("MemFree:")) freeKb = extractKb(line);
                }
                if (totalKb < 0) return null;
                long usedKb = availKb >= 0 ? totalKb - availKb : (freeKb >= 0 ? totalKb - freeKb : -1);
                if (usedKb < 0) return null;
                return new double[]{usedKb / 1024.0, totalKb / 1024.0};
            } catch (Exception e) {
                return null;
            }
        }

        private static long extractKb(String line) {
            String digits = line.replaceAll("[^0-9]", "");
            return digits.isEmpty() ? -1 : Long.parseLong(digits);
        }

        /** /proc/net/dev -> mapa interfaz -> {rxBytes, rxPaquetes, txBytes, txPaquetes}. */
        static Map<String, long[]> readNetDev() {
            if (!LINUX) return null;
            Map<String, long[]> map = new LinkedHashMap<>();
            try (BufferedReader r = new BufferedReader(new java.io.FileReader("/proc/net/dev"))) {
                String line;
                int lineNo = 0;
                while ((line = r.readLine()) != null) {
                    lineNo++;
                    if (lineNo <= 2) continue;
                    int idx = line.indexOf(':');
                    if (idx < 0) continue;
                    String name = line.substring(0, idx).trim();
                    String[] nums = line.substring(idx + 1).trim().split("\\s+");
                    if (nums.length < 16) continue;
                    long rxBytes = Long.parseLong(nums[0]);
                    long rxPackets = Long.parseLong(nums[1]);
                    long txBytes = Long.parseLong(nums[8]);
                    long txPackets = Long.parseLong(nums[9]);
                    map.put(name, new long[]{rxBytes, rxPackets, txBytes, txPackets});
                }
            } catch (Exception e) {
                return null;
            }
            return map;
        }

        static double[] diskUsageGB() {
            try {
                java.io.File root = new java.io.File("/");
                long total = root.getTotalSpace();
                long free = root.getFreeSpace();
                if (total <= 0) return null;
                return new double[]{(total - free) / 1e9, total / 1e9};
            } catch (Exception e) {
                return null;
            }
        }

        static double pingMs(String host, int timeoutMs) {
            try {
                long start = System.nanoTime();
                boolean reachable = InetAddress.getByName(host).isReachable(timeoutMs);
                long elapsed = (System.nanoTime() - start) / 1_000_000;
                return reachable ? elapsed : -1;
            } catch (Exception e) {
                return -1;
            }
        }

        static String osSummary() {
            return System.getProperty("os.name") + " " + System.getProperty("os.version")
                    + " (" + System.getProperty("os.arch") + ")";
        }

        static int processors() {
            return Runtime.getRuntime().availableProcessors();
        }

        static long jvmUptimeSeconds() {
            try {
                return ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
            } catch (Exception e) {
                return 0;
            }
        }
    }

    // =====================================================================
    // SIMULATION ENGINE
    // =====================================================================

    static final class SimulationEngine {
        volatile Scenario scenario = Scenario.NORMAL;
        volatile int ticksRemaining = 0;
        private final LogService logs;

        SimulationEngine(LogService logs) { this.logs = logs; }

        double cpuBias() {
            switch (scenario) {
                case CPU_SPIKE: return 45;
                case MEMORY_PRESSURE: return 8;
                default: return 0;
            }
        }

        double ramBias() {
            return scenario == Scenario.MEMORY_PRESSURE ? 35 : 0;
        }

        double latencyBiasMs() {
            switch (scenario) {
                case NET_DEGRADED: return 180;
                case PACKET_LOSS: return 60;
                default: return 0;
            }
        }

        double lossBiasPercent() {
            return scenario == Scenario.PACKET_LOSS ? 12 : 0;
        }

        synchronized void trigger(Scenario s, int durationTicks) {
            scenario = s;
            ticksRemaining = durationTicks;
            if (s != Scenario.NORMAL) {
                logs.warn("Escenario de simulacion activado: " + s + " (" + durationTicks + " ciclos)");
            } else {
                logs.info("Simulacion reiniciada a estado NORMAL.");
            }
        }

        synchronized void tick() {
            if (scenario == Scenario.NORMAL) return;
            if (ticksRemaining > 0) {
                ticksRemaining--;
                if (ticksRemaining == 0) {
                    if (scenario == Scenario.RECOVERY) {
                        scenario = Scenario.NORMAL;
                        logs.info("Sistema estabilizado. Escenario NORMAL restaurado.");
                    } else {
                        Scenario prev = scenario;
                        scenario = Scenario.RECOVERY;
                        ticksRemaining = 5;
                        logs.info("Iniciando recuperacion tras escenario " + prev + ".");
                    }
                }
            }
        }
    }

    // =====================================================================
    // METRICS SERVICE
    // =====================================================================

    static final class MetricsService {
        private static final int HISTORY_SIZE = 60;
        private final Deque<Double> cpuHistory = new ArrayDeque<>();
        private final Deque<Double> ramHistory = new ArrayDeque<>();
        private final Deque<Double> netHistory = new ArrayDeque<>();

        private double simCpu = 18;
        private double simRamPercent = 35;
        private double simNetIn = 120, simNetOut = 60;
        private long[] prevCpuJiffies = null;
        private Map<String, long[]> prevNetDev = null;
        private long prevNetSampleNanos = 0;

        private final SimulationEngine sim;
        MetricsService(SimulationEngine sim) { this.sim = sim; }

        synchronized Snapshot sample() {
            Snapshot s = new Snapshot();
            s.time = Instant.now();

            // CPU
            long[] cj = SysReader.readCpuJiffies();
            if (cj != null && prevCpuJiffies != null) {
                long dBusy = cj[0] - prevCpuJiffies[0];
                long dTotal = cj[1] - prevCpuJiffies[1];
                double real = dTotal > 0 ? (dBusy * 100.0 / dTotal) : 0;
                s.cpuPercent = clamp(real + sim.cpuBias(), 0, 100);
                s.cpuOrigin = Origin.REAL;
            } else {
                simCpu = smoothWalk(simCpu, 2.5, 5, 90);
                s.cpuPercent = clamp(simCpu + sim.cpuBias(), 0, 100);
                s.cpuOrigin = Origin.SIM;
            }
            prevCpuJiffies = cj;

            // RAM
            double[] mem = SysReader.readMemInfo();
            if (mem != null) {
                s.ramTotalMB = mem[1];
                s.ramUsedMB = Math.min(mem[1], mem[0] + sim.ramBias() / 100.0 * mem[1]);
                s.ramOrigin = Origin.REAL;
            } else {
                simRamPercent = clamp(smoothWalk(simRamPercent, 1.5, 15, 85) + sim.ramBias(), 0, 100);
                s.ramTotalMB = 8192;
                s.ramUsedMB = s.ramTotalMB * simRamPercent / 100.0;
                s.ramOrigin = Origin.SIM;
            }

            // DISCO
            double[] disk = SysReader.diskUsageGB();
            if (disk != null) {
                s.diskUsedGB = disk[0]; s.diskTotalGB = disk[1]; s.diskOrigin = Origin.REAL;
            } else {
                s.diskTotalGB = 500; s.diskUsedGB = 210; s.diskOrigin = Origin.SIM;
            }

            // RED (agregada)
            Map<String, long[]> nd = SysReader.readNetDev();
            long now = System.nanoTime();
            if (nd != null && prevNetDev != null && prevNetSampleNanos > 0) {
                double elapsedSec = (now - prevNetSampleNanos) / 1e9;
                long dRx = 0, dTx = 0;
                for (Map.Entry<String, long[]> e : nd.entrySet()) {
                    if (e.getKey().equals("lo")) continue;
                    long[] prev = prevNetDev.get(e.getKey());
                    if (prev == null) continue;
                    dRx += Math.max(0, e.getValue()[0] - prev[0]);
                    dTx += Math.max(0, e.getValue()[2] - prev[2]);
                }
                if (elapsedSec > 0.1) {
                    s.netInKbps = (dRx / 1024.0) / elapsedSec;
                    s.netOutKbps = (dTx / 1024.0) / elapsedSec;
                    s.netOrigin = Origin.REAL;
                } else {
                    s.netInKbps = simNetIn; s.netOutKbps = simNetOut; s.netOrigin = Origin.SIM;
                }
            } else {
                simNetIn = smoothWalk(simNetIn, 15, 10, 900);
                simNetOut = smoothWalk(simNetOut, 8, 5, 400);
                s.netInKbps = simNetIn; s.netOutKbps = simNetOut; s.netOrigin = Origin.SIM;
            }
            if (nd != null) { prevNetDev = nd; prevNetSampleNanos = now; }

            // LATENCIA
            double ping = SysReader.pingMs("1.1.1.1", 800);
            if (ping >= 0) {
                s.latencyMs = ping + sim.latencyBiasMs();
                s.latencyOrigin = Origin.REAL;
            } else {
                s.latencyMs = clamp(20 + sim.latencyBiasMs() + (Math.random() * 6 - 3), 1, 999);
                s.latencyOrigin = Origin.SIM;
            }

            s.uptimeSeconds = SysReader.jvmUptimeSeconds();
            s.health = computeHealth(s);

            pushHistory(cpuHistory, s.cpuPercent);
            pushHistory(ramHistory, s.ramUsedMB / s.ramTotalMB * 100.0);
            pushHistory(netHistory, s.netInKbps + s.netOutKbps);

            return s;
        }

        private Health computeHealth(Snapshot s) {
            double ramPct = s.ramUsedMB / s.ramTotalMB * 100.0;
            if (s.cpuPercent > 90 || ramPct > 92 || s.latencyMs > 400) return Health.CRITICAL;
            if (s.cpuPercent > 75 || ramPct > 80 || s.latencyMs > 150) return Health.WARNING;
            return Health.HEALTHY;
        }

        private static double smoothWalk(double current, double maxStep, double min, double max) {
            double delta = (Math.random() * 2 - 1) * maxStep;
            double v = current + delta;
            if (Math.random() < 0.03) v += (Math.random() * 2 - 1) * maxStep * 4; // evento ocasional
            return clamp(v, min, max);
        }

        private static double clamp(double v, double min, double max) {
            return Math.max(min, Math.min(max, v));
        }

        private void pushHistory(Deque<Double> dq, double v) {
            dq.addLast(v);
            while (dq.size() > HISTORY_SIZE) dq.removeFirst();
        }

        synchronized List<Double> cpuHistory() { return new ArrayList<>(cpuHistory); }
        synchronized List<Double> ramHistory() { return new ArrayList<>(ramHistory); }
        synchronized List<Double> netHistory() { return new ArrayList<>(netHistory); }
    }

    // =====================================================================
    // PROCESS SERVICE (PID/nombre reales via ProcessHandle; CPU/RAM simulados)
    // =====================================================================

    static final class ProcessService {
        private volatile List<Proc> cache = new ArrayList<>();
        private final Map<Long, double[]> simState = new java.util.HashMap<>();
        private final java.util.Random rnd = new java.util.Random();

        synchronized List<Proc> refresh() {
            List<Proc> list = new ArrayList<>();
            try {
                List<ProcessHandle> handles = ProcessHandle.allProcesses()
                        .limit(60)
                        .collect(Collectors.toList());
                for (ProcessHandle ph : handles) {
                    Proc p = new Proc();
                    p.pid = ph.pid();
                    String cmd = ph.info().command().orElse(null);
                    p.name = cmd != null ? shortName(cmd) : ("proc-" + p.pid);
                    double[] st = simState.computeIfAbsent(p.pid,
                            k -> new double[]{2 + rnd.nextDouble() * 8, 40 + rnd.nextDouble() * 400});
                    st[0] = clampVal(st[0] + (rnd.nextDouble() * 2 - 1) * 1.5, 0.1, 95);
                    st[1] = clampVal(st[1] + (rnd.nextDouble() * 2 - 1) * 10, 10, 4000);
                    p.cpuPercent = st[0];
                    p.ramMB = st[1];
                    p.state = ph.isAlive() ? ProcState.RUNNING : ProcState.STOPPED;
                    p.priority = 10 + (int) (p.pid % 10);
                    p.origin = Origin.REAL;
                    list.add(p);
                }
                if (list.isEmpty()) list = simulateFallback();
            } catch (Exception e) {
                list = simulateFallback();
            }
            cache = list;
            return list;
        }

        private List<Proc> simulateFallback() {
            List<Proc> list = new ArrayList<>();
            String[] names = {"sentinel-core", "sshd", "systemd", "dbus-daemon", "Xorg",
                    "gnome-shell", "java", "bash", "cron", "networkd"};
            for (int i = 0; i < names.length; i++) {
                Proc p = new Proc();
                p.pid = 1000 + i; p.name = names[i];
                p.cpuPercent = rnd.nextDouble() * 30;
                p.ramMB = 50 + rnd.nextDouble() * 500;
                p.state = ProcState.RUNNING; p.priority = 10 + i; p.origin = Origin.SIM;
                list.add(p);
            }
            return list;
        }

        private String shortName(String path) {
            String norm = path.replace('\\', '/');
            String[] parts = norm.split("/");
            return parts[parts.length - 1];
        }

        private double clampVal(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }

        List<Proc> current() { return cache; }
    }

    // =====================================================================
    // NETWORK SERVICE
    // =====================================================================

    static final class NetworkService {
        private Map<String, long[]> prevSample = null;
        private long prevNanos = 0;
        private final java.util.Random rnd = new java.util.Random();
        private final Map<String, double[]> simTraffic = new java.util.HashMap<>();
        private final SimulationEngine sim;

        NetworkService(SimulationEngine sim) { this.sim = sim; }

        synchronized List<Iface> refresh() {
            List<Iface> list = new ArrayList<>();
            Map<String, long[]> nd = SysReader.readNetDev();
            long now = System.nanoTime();
            double elapsedSec = prevNanos > 0 ? (now - prevNanos) / 1e9 : -1;
            try {
                Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
                while (nis != null && nis.hasMoreElements()) {
                    NetworkInterface ni = nis.nextElement();
                    Iface f = new Iface();
                    f.name = ni.getName();
                    f.up = ni.isUp();
                    String ip = "-";
                    Enumeration<InetAddress> addrs = ni.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        InetAddress a = addrs.nextElement();
                        if (a instanceof java.net.Inet4Address) { ip = a.getHostAddress(); break; }
                    }
                    f.ip = ip;

                    long[] cur = nd != null ? nd.get(f.name) : null;
                    long[] prev = prevSample != null ? prevSample.get(f.name) : null;
                    if (cur != null && prev != null && elapsedSec > 0.1) {
                        f.rxKbps = Math.max(0, (cur[0] - prev[0]) / 1024.0) / elapsedSec;
                        f.txKbps = Math.max(0, (cur[2] - prev[2]) / 1024.0) / elapsedSec;
                        f.packetsRx = cur[1]; f.packetsTx = cur[3];
                        f.trafficOrigin = Origin.REAL; f.packetsOrigin = Origin.REAL;
                    } else {
                        double[] st = simTraffic.computeIfAbsent(f.name,
                                k -> new double[]{20 + rnd.nextDouble() * 80, 10 + rnd.nextDouble() * 40});
                        st[0] = Math.max(0, st[0] + (rnd.nextDouble() * 2 - 1) * 10);
                        st[1] = Math.max(0, st[1] + (rnd.nextDouble() * 2 - 1) * 6);
                        f.rxKbps = st[0]; f.txKbps = st[1];
                        f.packetsRx = (long) (f.rxKbps * 7); f.packetsTx = (long) (f.txKbps * 7);
                        f.trafficOrigin = Origin.SIM; f.packetsOrigin = Origin.SIM;
                    }

                    double basePing = f.name.equals("lo") ? 0.1 : (5 + rnd.nextDouble() * 15);
                    f.latencyMs = basePing + sim.latencyBiasMs();
                    f.lossPercent = Math.max(0, sim.lossBiasPercent() + rnd.nextDouble() * 1.5);

                    list.add(f);
                }
            } catch (SocketException e) {
                Iface f = new Iface();
                f.name = "eth0"; f.ip = "10.0.0.5"; f.up = true;
                f.rxKbps = 120; f.txKbps = 45; f.trafficOrigin = Origin.SIM;
                f.packetsRx = 800; f.packetsTx = 300; f.packetsOrigin = Origin.SIM;
                f.latencyMs = 12; f.lossPercent = 0.2;
                list.add(f);
            }
            if (nd != null) { prevSample = nd; prevNanos = now; }
            return list;
        }
    }

    // =====================================================================
    // LOG SERVICE
    // =====================================================================

    static final class LogService {
        private final List<LogEntry> entries = java.util.Collections.synchronizedList(new ArrayList<>());
        private static final int MAX = 500;
        private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss")
                .withZone(ZoneId.systemDefault());

        void info(String m) { add(LogLevel.INFO, m); }
        void warn(String m) { add(LogLevel.WARN, m); }
        void error(String m) { add(LogLevel.ERROR, m); }
        void critical(String m) { add(LogLevel.CRITICAL, m); }

        void add(LogLevel lvl, String msg) {
            synchronized (entries) {
                entries.add(new LogEntry(lvl, msg));
                while (entries.size() > MAX) entries.remove(0);
            }
        }

        List<LogEntry> all() { synchronized (entries) { return new ArrayList<>(entries); } }

        List<LogEntry> filter(LogLevel lvl, String query) {
            List<LogEntry> out = new ArrayList<>();
            for (LogEntry e : all()) {
                if (lvl != null && e.level != lvl) continue;
                if (query != null && !query.isEmpty()
                        && !e.message.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) continue;
                out.add(e);
            }
            return out;
        }

        void clear() { entries.clear(); }

        static String format(LogEntry e) {
            String lvl = String.format("%-8s", e.level.name());
            return "[" + FMT.format(e.time) + "] " + lvl + e.message;
        }
    }

    // =====================================================================
    // TOPOLOGY SERVICE
    // =====================================================================

    static final class TopologyService {
        final Map<String, Node> nodes = new LinkedHashMap<>();
        private final SimulationEngine sim;
        private final java.util.Random rnd = new java.util.Random();
        private String forcedDownNode = null;

        TopologyService(SimulationEngine sim) {
            this.sim = sim;
            addNode("Router", "ROUTER");
            addNode("Gateway", "GATEWAY");
            addNode("Server", "SERVER");
            addNode("Database", "DATABASE");
            addNode("Application", "APPLICATION");
            addNode("Workstation", "WORKSTATION");
            link("Router", "Gateway");
            link("Router", "Server");
            link("Server", "Database");
            link("Server", "Application");
            link("Gateway", "Workstation");
        }

        private void addNode(String name, String type) {
            Node n = new Node(); n.name = name; n.type = type; nodes.put(name, n);
        }

        private void link(String parent, String child) { nodes.get(parent).children.add(child); }

        synchronized void tick(LogService logs) {
            if (sim.scenario == Scenario.NODE_DOWN) {
                if (forcedDownNode == null) {
                    String[] candidates = {"Database", "Application", "Server"};
                    forcedDownNode = candidates[rnd.nextInt(candidates.length)];
                    nodes.get(forcedDownNode).status = NodeStatus.OFFLINE;
                    logs.critical("Nodo caido detectado: " + forcedDownNode);
                }
            } else if (forcedDownNode != null) {
                if (sim.scenario == Scenario.NORMAL) {
                    nodes.get(forcedDownNode).status = NodeStatus.ONLINE;
                    logs.info("Nodo " + forcedDownNode + " restaurado (ONLINE).");
                    forcedDownNode = null;
                } else {
                    nodes.get(forcedDownNode).status = NodeStatus.DEGRADED;
                }
            } else {
                for (Node n : nodes.values()) {
                    if (n.status == NodeStatus.OFFLINE) continue;
                    if (rnd.nextDouble() < 0.02) {
                        n.status = NodeStatus.DEGRADED;
                        logs.warn("Latencia elevada detectada en nodo " + n.name);
                    } else if (n.status == NodeStatus.DEGRADED && rnd.nextDouble() < 0.4) {
                        n.status = NodeStatus.ONLINE;
                    }
                }
            }
        }

        synchronized Collection<Node> all() { return nodes.values(); }
        synchronized Node get(String name) { return nodes.get(name); }
    }

    // =====================================================================
    // INCIDENT SERVICE
    // =====================================================================

    static final class IncidentService {
        private final List<Incident> incidents = new ArrayList<>();
        private final AtomicInteger counter = new AtomicInteger(1);
        private final LogService logs;
        private final java.util.Set<String> openComponents = new java.util.HashSet<>();

        IncidentService(LogService logs) { this.logs = logs; }

        synchronized void evaluate(Snapshot s, List<Iface> ifaces, Collection<Node> nodes) {
            double ramPct = s.ramUsedMB / s.ramTotalMB * 100.0;

            if (s.cpuPercent > 85) raise(Severity.HIGH, "CPU", "Uso de CPU elevado: " + fmt(s.cpuPercent) + "%");
            else clear("CPU");

            if (ramPct > 85) raise(Severity.HIGH, "RAM", "Uso de memoria elevado: " + fmt(ramPct) + "%");
            else clear("RAM");

            if (s.latencyMs > 150) raise(Severity.MEDIUM, "NETWORK", "Latencia de red elevada: " + fmt(s.latencyMs) + " ms");
            else clear("NETWORK");

            for (Iface f : ifaces) {
                String key = "NET:" + f.name;
                if (f.lossPercent > 5) raise(Severity.MEDIUM, key, "Perdida de paquetes en " + f.name + ": " + fmt(f.lossPercent) + "%");
                else clear(key);
            }

            for (Node n : nodes) {
                if (n.status == NodeStatus.OFFLINE) raise(Severity.CRITICAL, n.name, "Nodo " + n.name + " fuera de linea (OFFLINE)");
                else if (n.status == NodeStatus.DEGRADED) raise(Severity.LOW, n.name, "Nodo " + n.name + " degradado");
                else clear(n.name);
            }
        }

        private void raise(Severity sev, String component, String desc) {
            if (openComponents.contains(component)) return;
            Incident inc = new Incident();
            inc.id = "INC-" + String.format("%04d", counter.getAndIncrement());
            inc.severity = sev; inc.time = Instant.now(); inc.component = component;
            inc.description = desc; inc.status = IncStatus.OPEN;
            incidents.add(0, inc);
            openComponents.add(component);
            LogLevel lvl = sev == Severity.CRITICAL ? LogLevel.CRITICAL : sev == Severity.HIGH ? LogLevel.ERROR : LogLevel.WARN;
            logs.add(lvl, "Incidente generado " + inc.id + " - " + desc);
        }

        private void clear(String component) { openComponents.remove(component); }

        private String fmt(double v) { return String.format(Locale.ROOT, "%.1f", v); }

        synchronized List<Incident> all() { return new ArrayList<>(incidents); }

        synchronized boolean updateStatus(String id, IncStatus status) {
            for (Incident i : incidents) {
                if (i.id.equalsIgnoreCase(id)) {
                    i.status = status;
                    logs.info("Incidente " + i.id + " marcado como " + status);
                    return true;
                }
            }
            return false;
        }
    }

    // =====================================================================
    // REPORT SERVICE
    // =====================================================================

    static final class ReportService {
        static String build(Snapshot s, List<Proc> procs, List<Iface> ifaces, Collection<Node> nodes,
                             List<Incident> incidents, List<LogEntry> recentLogs) {
            StringBuilder sb = new StringBuilder();
            DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

            sb.append("==================================================\n");
            sb.append(" SENTINELLAB - INFORME DE ESTADO DEL SISTEMA\n");
            sb.append("==================================================\n");
            sb.append("Fecha/Hora: ").append(df.format(Instant.now())).append("\n");
            sb.append("Estado general: ").append(s.health).append("\n\n");

            sb.append("-- RECURSOS --\n");
            sb.append(String.format(Locale.ROOT, "CPU: %.1f%% [%s]\n", s.cpuPercent, s.cpuOrigin));
            sb.append(String.format(Locale.ROOT, "RAM: %.0f/%.0f MB (%.1f%%) [%s]\n",
                    s.ramUsedMB, s.ramTotalMB, s.ramUsedMB / s.ramTotalMB * 100.0, s.ramOrigin));
            sb.append(String.format(Locale.ROOT, "Disco: %.1f/%.1f GB [%s]\n", s.diskUsedGB, s.diskTotalGB, s.diskOrigin));
            sb.append(String.format(Locale.ROOT, "Red: In %.1f kb/s / Out %.1f kb/s [%s]\n", s.netInKbps, s.netOutKbps, s.netOrigin));
            sb.append(String.format(Locale.ROOT, "Latencia: %.1f ms [%s]\n", s.latencyMs, s.latencyOrigin));
            sb.append("Uptime JVM: ").append(formatUptime(s.uptimeSeconds)).append("\n\n");

            sb.append("-- PROCESOS (").append(procs.size()).append(") --\n");
            int shown = 0;
            for (Proc p : procs) {
                if (shown++ >= 10) break;
                sb.append(String.format(Locale.ROOT, "  PID %-6d %-20s CPU %5.1f%%  RAM %6.0fMB  %s\n",
                        p.pid, p.name, p.cpuPercent, p.ramMB, p.state));
            }

            sb.append("\n-- RED / INTERFACES --\n");
            for (Iface f : ifaces) {
                sb.append(String.format(Locale.ROOT, "  %-10s %-15s %-4s RX %7.1fkb/s TX %7.1fkb/s Loss %.1f%%\n",
                        f.name, f.ip, f.up ? "UP" : "DOWN", f.rxKbps, f.txKbps, f.lossPercent));
            }

            sb.append("\n-- TOPOLOGIA --\n");
            for (Node n : nodes) sb.append("  ").append(n.name).append(": ").append(n.status).append("\n");

            sb.append("\n-- INCIDENTES (").append(incidents.size()).append(") --\n");
            if (incidents.isEmpty()) sb.append("  Sin incidentes registrados.\n");
            for (Incident i : incidents) {
                sb.append(String.format("  %s [%s] %-10s %s - %s\n", i.id, i.severity, i.component, i.status, i.description));
            }

            sb.append("\n-- EVENTOS RECIENTES --\n");
            for (LogEntry e : recentLogs) sb.append("  ").append(LogService.format(e)).append("\n");

            sb.append("\n==================================================\n");
            sb.append(" Fin del informe. SentinelLab v1.0\n");
            sb.append("==================================================\n");
            return sb.toString();
        }

        static String formatUptime(long seconds) {
            long h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
            return String.format("%02dh %02dm %02ds", h, m, s);
        }
    }

    // =====================================================================
    // ASCII / RENDER HELPERS (estetica Fedora - azul)
    // =====================================================================

    static final class Ascii {
        static final String[] FEDORA_LOGO = {
                "     ███████████████          ",
                "   ██               ██        ",
                "  ██     ███████     ██       ",
                " ██     ██     ██     ██      ",
                " ██    ██   ████████████      ",
                " ██    ██   ██                ",
                " ██    ██   ██████             ",
                " ██    ██   ██                ",
                "  ██   ██   ██                ",
                "   ██  ██   ██                ",
                "    ██████████                "
        };

        static void printLogo() {
            for (String line : FEDORA_LOGO) System.out.println(Ansi.BLUE + Ansi.BOLD + line + Ansi.RESET);
        }

        static String bar(double percent, int width, Health forcedColor) {
            percent = Math.max(0, Math.min(100, percent));
            int filled = (int) Math.round(percent / 100.0 * width);
            String color = forcedColor != null ? colorFor(forcedColor) : colorForPercent(percent);
            StringBuilder sb = new StringBuilder();
            sb.append(color).append(Term.repeat("#", filled));
            sb.append(Ansi.DARK_GRAY).append(Term.repeat(".", width - filled));
            sb.append(Ansi.RESET);
            return sb.toString();
        }

        static String colorForPercent(double p) {
            if (p >= 85) return Ansi.RED;
            if (p >= 65) return Ansi.YELLOW;
            return Ansi.GREEN;
        }

        static String colorFor(Health h) {
            switch (h) {
                case CRITICAL: return Ansi.RED;
                case WARNING: return Ansi.YELLOW;
                default: return Ansi.GREEN;
            }
        }

        static String badge(Health h) {
            return colorFor(h) + Ansi.BOLD + " " + h.name() + " " + Ansi.RESET;
        }

        static String sparkline(List<Double> values) {
            char[] blocks = {'_', '.', ':', '-', '=', '+', '*', '#'};
            if (values.isEmpty()) return "";
            double max = 1;
            for (double v : values) max = Math.max(max, v);
            StringBuilder sb = new StringBuilder();
            sb.append(Ansi.LIGHT_BLUE);
            for (double v : values) {
                int idx = (int) Math.round((v / max) * (blocks.length - 1));
                idx = Math.max(0, Math.min(blocks.length - 1, idx));
                sb.append(blocks[idx]);
            }
            sb.append(Ansi.RESET);
            return sb.toString();
        }

        static String originTag(Origin o) {
            return o == Origin.REAL ? (Ansi.GREEN + "[REAL]" + Ansi.RESET) : (Ansi.GRAY + "[SIM]" + Ansi.RESET);
        }

        static void header(String title) {
            System.out.println(Ansi.BG_BLUE + Ansi.WHITE + Ansi.BOLD + Term.pad("  " + title, 78) + Ansi.RESET);
            System.out.println(Ansi.DARK_BLUE + Term.repeat("-", 78) + Ansi.RESET);
        }

        static void footer(String hint) {
            System.out.println(Ansi.DARK_BLUE + Term.repeat("-", 78) + Ansi.RESET);
            System.out.println(Ansi.GRAY + hint + Ansi.RESET);
        }
    }

    // =====================================================================
    // TERMINAL / COMMAND INTERPRETER
    // =====================================================================

    interface CliCommand {
        void exec(String[] args, App app);
        String help();
    }

    static final class Terminal {
        final Map<String, CliCommand> commands = new LinkedHashMap<>();
        final List<String> history = new ArrayList<>();

        Terminal() { register(); }

        private void register() {
            commands.put("help", new CliCommand() {
                public void exec(String[] a, App ap) { printHelp(); }
                public String help() { return "Muestra los comandos disponibles"; }
            });
            commands.put("status", new CliCommand() {
                public void exec(String[] a, App ap) { ap.printStatusSummary(); }
                public String help() { return "Resumen de estado del sistema"; }
            });
            commands.put("network", new CliCommand() {
                public void exec(String[] a, App ap) { ap.printNetworkSummary(); }
                public String help() { return "Resumen de interfaces de red"; }
            });
            commands.put("processes", new CliCommand() {
                public void exec(String[] a, App ap) { ap.printProcessSummary(); }
                public String help() { return "Lista de procesos activos"; }
            });
            commands.put("logs", new CliCommand() {
                public void exec(String[] a, App ap) { ap.printLogsTail(15); }
                public String help() { return "Ultimos eventos de log"; }
            });
            commands.put("scan", new CliCommand() {
                public void exec(String[] a, App ap) { ap.runScan(); }
                public String help() { return "Ejecuta un escaneo de infraestructura"; }
            });
            commands.put("incident", new CliCommand() {
                public void exec(String[] a, App ap) { ap.printIncidentSummary(a); }
                public String help() { return "incident list | incident resolve <ID> | incident investigate <ID>"; }
            });
            commands.put("report", new CliCommand() {
                public void exec(String[] a, App ap) { ap.generateAndShowReport(); }
                public String help() { return "Genera un informe de estado"; }
            });
            commands.put("simulate", new CliCommand() {
                public void exec(String[] a, App ap) { ap.runSimulateCommand(a); }
                public String help() { return "simulate <cpu|memoria|red|paquetes|nodo|reset>"; }
            });
            commands.put("clear", new CliCommand() {
                public void exec(String[] a, App ap) { Term.clear(); }
                public String help() { return "Limpia la pantalla"; }
            });
        }
private void printHelp() {

    System.out.println(
            Ansi.LIGHT_BLUE + "Comandos disponibles:" + Ansi.RESET
    );

    for (Map.Entry<String, CliCommand> e : commands.entrySet()) {

        System.out.println(
                "  "
                + Ansi.BOLD
                + Term.pad(e.getKey(), 12)
                + Ansi.RESET
                + Ansi.GRAY
                + e.getValue().help()
                + Ansi.RESET
        );
    }

    System.out.println(
            "  "
            + Term.pad("history", 12)
            + "Muestra el historial de comandos"
    );

    System.out.println(
            "  "
            + Term.pad("!N", 12)
            + "Repite el comando numero N del historial"
    );

    System.out.println(
            "  "
            + Term.pad("exit", 12)
            + "Vuelve al menu principal"
    );
}

void execute(String line, App app) {

    if (line == null || line.trim().isEmpty()) {
        return;
    }

    history.add(line);

    String[] tokens = line.trim().split("\\s+");

    String cmd = tokens[0].toLowerCase(Locale.ROOT);

    String[] args = Arrays.copyOfRange(tokens, 1, tokens.length);

    CliCommand c = commands.get(cmd);

    if (c == null) {

        System.out.println(
                Ansi.RED
                        + "Comando no reconocido: "
                        + cmd
                        + Ansi.RESET
                        + " (escribe 'help')"
        );

        return;
    }

    try {

        c.exec(args, app);

    } catch (Exception e) {

        System.out.println(
                Ansi.RED
                        + "Error ejecutando comando: "
                        + e.getMessage()
                        + Ansi.RESET
        );

        app.logs.error(
                "Error en comando de terminal '"
                        + cmd
                        + "': "
                        + e.getMessage()
        );
    }
}


// =====================================================================
// APP (controlador principal / navegacion / pantallas)
// =====================================================================

static final class App {

    final LogService logs = new LogService();

    final SimulationEngine sim = new SimulationEngine(logs);

    final MetricsService metrics = new MetricsService(sim);

    final ProcessService processes = new ProcessService();

    final NetworkService network = new NetworkService(sim);

    final TopologyService topology = new TopologyService(sim);

    final IncidentService incidents = new IncidentService(logs);

    final Terminal terminal = new Terminal();

    final BufferedReader stdin =
            new BufferedReader(
                    new InputStreamReader(System.in)
            );

    volatile Snapshot lastSnapshot;

    volatile List<Proc> lastProcs =
            new ArrayList<>();

    volatile List<Iface> lastIfaces =
            new ArrayList<>();

    volatile boolean running = true;

    private int animTick = 0;

    Thread ticker;

    void start() throws IOException {

        logs.info("Monitor iniciado");

        logs.info("Interfaz de red detectada");

        lastSnapshot = metrics.sample();

        lastProcs = processes.refresh();

        lastIfaces = network.refresh();

        ticker = new Thread(
                this::backgroundLoop,
                "sentinel-ticker"
        );

        ticker.setDaemon(true);

        ticker.start();

        showSplash();

        mainLoop();
    }

    void backgroundLoop() {

        while (running) {

            try {

                lastSnapshot = metrics.sample();

                lastProcs = processes.refresh();

                lastIfaces = network.refresh();

                topology.tick(logs);

                sim.tick();

                incidents.evaluate(
                        lastSnapshot,
                        lastIfaces,
                        topology.all()
                );

            } catch (Exception e) {

                logs.error(
                        "Error en ciclo de monitoreo: "
                                + e.getMessage()
                );
            }

            Term.sleep(1000);
        }
    }

    void mainLoop() throws IOException {

        while (running) {

            renderMainMenu();

            String choice = prompt(
                    Ansi.LIGHT_BLUE
                            + "sentinel(menu)> "
                            + Ansi.RESET
            )
                    .trim()
                    .toLowerCase(Locale.ROOT);

            switch (choice) {

                case "1":
                case "dashboard":
                    screenDashboard();
                    break;

                case "2":
                case "monitor":
                    screenMonitor();
                    break;

                case "3":
                case "network":
                    screenNetwork();
                    break;

                case "4":
                case "processes":
                    screenProcesses();
                    break;

                case "5":
                case "terminal":
                    screenTerminal();
                    break;

                case "6":
                case "logs":
                    screenLogs();
                    break;

                case "7":
                case "topology":
                    screenTopology();
                    break;

                case "8":
                case "simulation":
                    screenSimulation();
                    break;

                case "9":
                case "incidents":
                    screenIncidents();
                    break;

                case "0":
                case "report":
                    screenReport();
                    break;

                case "exit":
                case "salir":
                case "q":
                    confirmExit();
                    break;

                case "":
                    break;

                default:

                    System.out.println(
                            Ansi.RED
                                    + "Opcion no valida."
                                    + Ansi.RESET
                    );

                    Term.sleep(500);
            }
        }
    }

    // --- NETWORK ---

    void screenNetwork() throws IOException {

        Term.clear();

        Ascii.header(
                "NETWORK -- Interfaces detectadas"
        );

        List<Iface> ifaces = lastIfaces;

        if (ifaces.isEmpty()) {

            System.out.println(
                    Ansi.GRAY
                            + "  No se detectaron interfaces."
                            + Ansi.RESET
            );
        }

        System.out.printf(
                Locale.ROOT,
                "  %-10s %-15s %-6s %10s %10s %8s %8s %8s%n",
                "IFAZ",
                "IP",
                "ESTADO",
                "RX kb/s",
                "TX kb/s",
                "LAT ms",
                "PKT RX",
                "LOSS%%"
        );

        for (Iface f : ifaces) {

            System.out.printf(
                    Locale.ROOT,
                    "  %-10s %-15s %-6s %10.1f %10.1f %8.1f %8d %7.1f%%  %s%n",
                    f.name,
                    f.ip,
                    f.up ? "UP" : "DOWN",
                    f.rxKbps,
                    f.txKbps,
                    f.latencyMs,
                    f.packetsRx,
                    f.lossPercent,
                    Ascii.originTag(f.trafficOrigin)
            );
        }

        Ascii.footer(
                "ENTER para volver."
        );

        stdin.readLine();
    }

    // --- PROCESSES ---

    void screenProcesses() throws IOException {

        String filterState = null;

        String query = null;

        String sortBy = "cpu";

        while (true) {

            List<Proc> list =
                    new ArrayList<>(lastProcs);

            if (query != null && !query.isEmpty()) {

                String q =
                        query.toLowerCase(Locale.ROOT);

                list.removeIf(
                        p ->
                                !p.name
                                        .toLowerCase(Locale.ROOT)
                                        .contains(q)
                                && !String
                                        .valueOf(p.pid)
                                        .contains(q)
                );
            }

            if (filterState != null) {

                final String stateFilter =
                        filterState;

                list.removeIf(
                        p ->
                                !p.state
                                        .name()
                                        .equalsIgnoreCase(
                                                stateFilter
                                        )
                );
            }

            switch (sortBy) {

                case "ram":

                    list.sort(
                            (a, b) ->
                                    Double.compare(
                                            b.ramMB,
                                            a.ramMB
                                    )
                    );

                    break;

                case "pid":

                    list.sort(
                            (a, b) ->
                                    Long.compare(
                                            a.pid,
                                            b.pid
                                    )
                    );

                    break;

                case "name":

                    list.sort(
                            (a, b) ->
                                    a.name
                                            .compareToIgnoreCase(
                                                    b.name
                                            )
                    );

                    break;

                default:

                    list.sort(
                            (a, b) ->
                                    Double.compare(
                                            b.cpuPercent,
                                            a.cpuPercent
                                    )
                    );
            }

            Term.clear();

            Ascii.header(
                    "PROCESSES -- "
                            + list.size()
                            + " procesos"
            );

            System.out.printf(
                    Locale.ROOT,
                    "  %-8s %-22s %8s %10s %-10s %5s%n",
                    "PID",
                    "PROCESO",
                    "CPU%%",
                    "RAM(MB)",
                    "ESTADO",
                    "PRIO"
            );

            int shown = 0;

            for (Proc p : list) {

                if (shown++ >= 20) {
                    break;
                }

                System.out.printf(
                        Locale.ROOT,
                        "  %-8d %-22s %7.1f%% %10.0f %-10s %5d%n",
                        p.pid,
                        trimTo(p.name, 22),
                        p.cpuPercent,
                        p.ramMB,
                        p.state,
                        p.priority
                );
            }

            System.out.println();

            System.out.println(
                    Ansi.GRAY
                            + "  PID y nombre: "
                            + Ascii.originTag(Origin.REAL)
                            + "   CPU/RAM/prioridad: "
                            + Ascii.originTag(Origin.SIM)
                            + Ansi.RESET
            );

            Ascii.footer(
                    "Comandos: sort cpu|ram|pid|name . "
                            + "buscar <texto> . "
                            + "estado <RUNNING|SLEEPING|STOPPED> . "
                            + "reset . salir"
            );

            String cmd =
                    prompt(
                            Ansi.LIGHT_BLUE
                                    + "processes> "
                                    + Ansi.RESET
                    )
                            .trim();

            if (cmd.equalsIgnoreCase("salir")
                    || cmd.equalsIgnoreCase("exit")) {

                break;

            } else if (cmd.startsWith("sort ")) {

                sortBy =
                        cmd.substring(5)
                                .trim()
                                .toLowerCase(Locale.ROOT);

            } else if (cmd.startsWith("buscar ")) {

                query =
                        cmd.substring(7).trim();

            } else if (cmd.startsWith("estado ")) {

                filterState =
                        cmd.substring(7).trim();

            } else if (cmd.equalsIgnoreCase("reset")) {

                query = null;

                filterState = null;

                sortBy = "cpu";
            }
        }
    }
}
        void showSplash() {
            Term.clear();
            Ascii.printLogo();
            System.out.println();
            System.out.println(Ansi.BOLD + Ansi.WHITE + "  SENTINELLAB" + Ansi.RESET);
            System.out.println(Ansi.GRAY + "  Infrastructure Operations Console  -  v1.0" + Ansi.RESET);
            System.out.println();
            System.out.println(Ansi.DIM + "  Sistema: " + SysReader.osSummary() + Ansi.RESET);
            System.out.println(Ansi.DIM + "  Procesadores: " + SysReader.processors() + Ansi.RESET);
            System.out.println();
            System.out.println(Ansi.GRAY + "  Presiona ENTER para continuar..." + Ansi.RESET);
            try { stdin.readLine(); } catch (IOException ignored) { }
        }

        String prompt(String p) throws IOException {
            System.out.print(p);
            System.out.flush();
            String line = stdin.readLine();
            return line == null ? "" : line;
        }
void screenNetwork() throws IOException {
    Term.clear();

    Ascii.header("NETWORK -- Interfaces detectadas");

    List<Iface> ifaces = lastIfaces;

    if (ifaces.isEmpty())
        System.out.println(Ansi.GRAY + "  No se detectaron interfaces." + Ansi.RESET);

    System.out.printf(Locale.ROOT,
            "  %-10s %-15s %-6s %10s %10s %8s %8s %8s%n",
            "IFAZ", "IP", "ESTADO", "RX kb/s", "TX kb/s",
            "LAT ms", "PKT RX", "LOSS%%");

    for (Iface f : ifaces) {
        System.out.printf(Locale.ROOT,
                "  %-10s %-15s %-6s %10.1f %10.1f %8.1f %8d %7.1f%%  %s%n",
                f.name, f.ip, f.up ? "UP" : "DOWN",
                f.rxKbps, f.txKbps, f.latencyMs,
                f.packetsRx, f.lossPercent,
                Ascii.originTag(f.trafficOrigin));
    }

    Ascii.footer("ENTER para volver.");
    stdin.readLine();
}

        void confirmExit() throws IOException {
            String c = prompt(Ansi.YELLOW + "Confirmas salir de SentinelLab? (s/n): " + Ansi.RESET);
            if (c.trim().equalsIgnoreCase("s")) {
                running = false;
                System.out.println(Ansi.BLUE + "Cerrando SentinelLab. Hasta pronto." + Ansi.RESET);
            }
        }

        void renderMainMenu() {
            Term.clear();
            Ascii.header("SENTINELLAB -- Infrastructure Operations Console  v1.0");
            Snapshot s = lastSnapshot;
            System.out.println("  Estado general: " + Ascii.badge(s.health)
                    + "   " + Ansi.GRAY + java.time.LocalTime.now().withNano(0) + Ansi.RESET);
            System.out.println();
            System.out.println(Ansi.BOLD + "  [1] Dashboard      [2] Monitor         [3] Network" + Ansi.RESET);
            System.out.println(Ansi.BOLD + "  [4] Processes      [5] Terminal        [6] Logs" + Ansi.RESET);
            System.out.println(Ansi.BOLD + "  [7] Topology       [8] Simulation      [9] Incidents" + Ansi.RESET);
            System.out.println(Ansi.BOLD + "  [0] Report                              exit) Salir" + Ansi.RESET);
            Ascii.footer("Escribe el numero/nombre de la seccion o 'exit' para salir. (Atajos Ctrl+1..7 no disponibles en TTY estandar; usa los numeros)");
        }

        // --- DASHBOARD ---
        void screenDashboard() throws IOException { liveView("DASHBOARD", this::renderDashboardBody); }

        void renderDashboardBody() {
            Snapshot s = lastSnapshot;
            double ramPct = s.ramUsedMB / s.ramTotalMB * 100.0;
            double diskPct = s.diskUsedGB / s.diskTotalGB * 100.0;
            System.out.println("  Estado general: " + Ascii.badge(s.health));
            System.out.println();
            printCard("CPU", String.format(Locale.ROOT, "%.1f%%", s.cpuPercent), Ascii.bar(s.cpuPercent, 30, null), Ascii.originTag(s.cpuOrigin));
            printCard("RAM", String.format(Locale.ROOT, "%.0f/%.0f MB", s.ramUsedMB, s.ramTotalMB), Ascii.bar(ramPct, 30, null), Ascii.originTag(s.ramOrigin));
            printCard("DISCO", String.format(Locale.ROOT, "%.0f/%.0f GB", s.diskUsedGB, s.diskTotalGB), Ascii.bar(diskPct, 30, null), Ascii.originTag(s.diskOrigin));
            printCard("RED", String.format(Locale.ROOT, "in %.0f / out %.0f kb/s", s.netInKbps, s.netOutKbps), "", Ascii.originTag(s.netOrigin));
            System.out.println();
            System.out.println("  Uptime: " + ReportService.formatUptime(s.uptimeSeconds)
                    + "   Procesos: " + lastProcs.size()
                    + "   Latencia: " + String.format(Locale.ROOT, "%.0fms", s.latencyMs) + " " + Ascii.originTag(s.latencyOrigin));
            System.out.println();
            System.out.println(Ansi.LIGHT_BLUE + "  Historial CPU:  " + Ansi.RESET + Ascii.sparkline(metrics.cpuHistory()));
            System.out.println(Ansi.LIGHT_BLUE + "  Historial RAM:  " + Ansi.RESET + Ascii.sparkline(metrics.ramHistory()));
            System.out.println(Ansi.LIGHT_BLUE + "  Historial RED:  " + Ansi.RESET + Ascii.sparkline(metrics.netHistory()));
        }

        void printCard(String label, String value, String bar, String tag) {
            System.out.println("  " + Ansi.BOLD + Term.pad(label, 8) + Ansi.RESET + Term.pad(value, 20) + " " + bar + "  " + tag);
        }

        // --- MONITOR ---
        void screenMonitor() throws IOException {
            liveView("MONITOR", () -> {
                Snapshot s = lastSnapshot;
                double ramPct = s.ramUsedMB / s.ramTotalMB * 100.0;
                double diskPct = s.diskUsedGB / s.diskTotalGB * 100.0;
                System.out.println("  CPU   " + Ascii.bar(s.cpuPercent, 40, null)
                        + String.format(Locale.ROOT, " %5.1f%% ", s.cpuPercent) + Ascii.originTag(s.cpuOrigin));
                System.out.println("        " + Ascii.sparkline(metrics.cpuHistory()));
                System.out.println();
                System.out.println("  RAM   " + Ascii.bar(ramPct, 40, null)
                        + String.format(Locale.ROOT, " %5.1f%% ", ramPct) + Ascii.originTag(s.ramOrigin));
                System.out.println("        " + String.format(Locale.ROOT, "%.0f MB usados de %.0f MB", s.ramUsedMB, s.ramTotalMB));
                System.out.println();
                System.out.println("  DISCO " + Ascii.bar(diskPct, 40, null)
                        + String.format(Locale.ROOT, " %5.1f%% ", diskPct) + Ascii.originTag(s.diskOrigin));
                System.out.println("        " + String.format(Locale.ROOT, "%.1f GB usados de %.1f GB", s.diskUsedGB, s.diskTotalGB));
                System.out.println();
                System.out.println("  RED   In " + String.format(Locale.ROOT, "%7.1f kb/s", s.netInKbps)
                        + "   Out " + String.format(Locale.ROOT, "%7.1f kb/s", s.netOutKbps) + "  " + Ascii.originTag(s.netOrigin));
                System.out.println("        " + Ascii.sparkline(metrics.netHistory()));
                System.out.println("        Latencia: " + String.format(Locale.ROOT, "%.1f ms", s.latencyMs) + " " + Ascii.originTag(s.latencyOrigin));
            });
        }

    // --- PROCESSES ---

void screenProcesses() throws IOException {

    String filterState = null;
    String query = null;
    String sortBy = "cpu";

    while (true) {

        List<Proc> list = new ArrayList<>(lastProcs);

        if (query != null && !query.isEmpty()) {
            String q = query.toLowerCase(Locale.ROOT);
            list.removeIf(p ->
                    !p.name.toLowerCase(Locale.ROOT).contains(q)
                    && !String.valueOf(p.pid).contains(q));
        }

        if (filterState != null) {
            final String stateFilter = filterState;
            list.removeIf(p ->
                    !p.state.name().equalsIgnoreCase(stateFilter));
        }

        switch (sortBy) {
            case "ram":
                list.sort((a, b) -> Double.compare(b.ramMB, a.ramMB));
                break;

            case "pid":
                list.sort((a, b) -> Long.compare(a.pid, b.pid));
                break;

            case "name":
                list.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
                break;

            default:
                list.sort((a, b) ->
                        Double.compare(b.cpuPercent, a.cpuPercent));
        }

        Term.clear();

        Ascii.header("PROCESSES -- " + list.size() + " procesos");

        System.out.printf(
                Locale.ROOT,
                "  %-8s %-22s %8s %10s %-10s %5s%n",
                "PID", "PROCESO", "CPU%%", "RAM(MB)", "ESTADO", "PRIO"
        );

        int shown = 0;

        for (Proc p : list) {

            if (shown++ >= 20)
                break;

            System.out.printf(
                    Locale.ROOT,
                    "  %-8d %-22s %7.1f%% %10.0f %-10s %5d%n",
                    p.pid,
                    trimTo(p.name, 22),
                    p.cpuPercent,
                    p.ramMB,
                    p.state,
                    p.priority
            );
        }

        System.out.println();

        System.out.println(
                Ansi.GRAY
                        + "  PID y nombre: "
                        + Ascii.originTag(Origin.REAL)
                        + "   CPU/RAM/prioridad: "
                        + Ascii.originTag(Origin.SIM)
                        + Ansi.RESET
        );

        Ascii.footer(
                "Comandos: sort cpu|ram|pid|name . buscar <texto> . "
                        + "estado <RUNNING|SLEEPING|STOPPED> . reset . salir"
        );

        String cmd = prompt(
                Ansi.LIGHT_BLUE + "processes> " + Ansi.RESET
        ).trim();

        if (cmd.equalsIgnoreCase("salir")
                || cmd.equalsIgnoreCase("exit")) {
            break;
        }

        else if (cmd.startsWith("sort ")) {
            sortBy = cmd.substring(5)
                    .trim()
                    .toLowerCase(Locale.ROOT);
        }

        else if (cmd.startsWith("buscar ")) {
            query = cmd.substring(7).trim();
        }

        else if (cmd.startsWith("estado ")) {
            filterState = cmd.substring(7).trim();
        }

        else if (cmd.equalsIgnoreCase("reset")) {
            query = null;
            filterState = null;
            sortBy = "cpu";
        }
    }
}
        private String trimTo(String s, int max) { return s.length() > max ? s.substring(0, max) : s; }

        // --- TERMINAL ---
        void screenTerminal() throws IOException {
            Term.clear();
            Ascii.header("TERMINAL -- SentinelLab Shell");
            System.out.println(Ansi.GRAY + "help = comandos . history = historial . !N = repetir comando N . exit = volver" + Ansi.RESET);
            while (true) {
                String line = prompt(Ansi.LIGHT_BLUE + "sentinel> " + Ansi.RESET);
                if (line == null) break;
                String trimmed = line.trim();
                if (trimmed.equalsIgnoreCase("exit")) break;
                if (trimmed.equalsIgnoreCase("history")) {
                    List<String> h = terminal.history;
                    for (int i = 0; i < h.size(); i++) System.out.println("  " + i + "  " + h.get(i));
                    continue;
                }
                if (trimmed.startsWith("!")) {
                    try {
                        int idx = Integer.parseInt(trimmed.substring(1));
                        if (idx >= 0 && idx < terminal.history.size()) {
                            line = terminal.history.get(idx);
                            System.out.println(Ansi.GRAY + "> " + line + Ansi.RESET);
                        } else {
                            System.out.println(Ansi.RED + "Indice de historial invalido." + Ansi.RESET);
                            continue;
                        }
                    } catch (NumberFormatException nfe) {
                        System.out.println(Ansi.RED + "Uso: !N" + Ansi.RESET);
                        continue;
                    }
                }
                terminal.execute(line, this);
            }
        }

        // --- LOGS ---
        void screenLogs() throws IOException {
            LogLevel filter = null; String query = null;
            while (true) {
                List<LogEntry> list = logs.filter(filter, query);
                Term.clear();
                Ascii.header("LOGS -- Visor de eventos (" + list.size() + ")");
                int from = Math.max(0, list.size() - 25);
                for (int i = from; i < list.size(); i++) {
                    LogEntry e = list.get(i);
                    System.out.println(colorForLevel(e.level) + LogService.format(e) + Ansi.RESET);
                }
                Ascii.footer("Comandos: nivel <INFO|WARN|ERROR|CRITICAL> . buscar <texto> . limpiar . reset . salir");
                String cmd = prompt(Ansi.LIGHT_BLUE + "logs> " + Ansi.RESET).trim();
                if (cmd.equalsIgnoreCase("salir") || cmd.equalsIgnoreCase("exit")) break;
                else if (cmd.startsWith("nivel ")) {
                    try { filter = LogLevel.valueOf(cmd.substring(6).trim().toUpperCase(Locale.ROOT)); }
                    catch (Exception e) { System.out.println(Ansi.RED + "Nivel invalido." + Ansi.RESET); Term.sleep(700); }
                } else if (cmd.startsWith("buscar ")) query = cmd.substring(7).trim();
                else if (cmd.equalsIgnoreCase("limpiar")) logs.clear();
                else if (cmd.equalsIgnoreCase("reset")) { filter = null; query = null; }
            }
        }

        private String colorForLevel(LogLevel l) {
            switch (l) {
                case CRITICAL: return Ansi.RED + Ansi.BOLD;
                case ERROR: return Ansi.RED;
                case WARN: return Ansi.YELLOW;
                default: return Ansi.GRAY;
            }
        }

        // --- TOPOLOGY ---
        void screenTopology() throws IOException { liveView("INFRASTRUCTURE TOPOLOGY", this::renderTopologyBody); }

        void renderTopologyBody() {
            animTick++;
            Node router = topology.get("Router");
            System.out.println("  " + nodeLabel(router));
            printChildren(router, "  ");
        }

        private void printChildren(Node parent, String prefix) {
            List<String> kids = parent.children;
            for (int i = 0; i < kids.size(); i++) {
                Node child = topology.get(kids.get(i));
                boolean last = (i == kids.size() - 1);
                String connector = last ? "+-- " : "|-- ";
                String traffic = (animTick % 2 == 0) ? ">" : ".";
                System.out.println(prefix + connector + nodeLabel(child) + "  " + Ansi.DIM + traffic + Ansi.RESET);
                printChildren(child, prefix + (last ? "    " : "|   "));
            }
        }

        private String nodeLabel(Node n) {
            String color;
            switch (n.status) {
                case ONLINE: color = Ansi.GREEN; break;
                case DEGRADED: color = Ansi.YELLOW; break;
                default: color = Ansi.RED;
            }
            return Ansi.BOLD + n.name + Ansi.RESET + " " + color + "*" + Ansi.RESET + " " + color + n.status + Ansi.RESET;
        }

        // --- SIMULATION ---
        void screenSimulation() throws IOException {
            while (true) {
                Term.clear();
                Ascii.header("SIMULATION -- Motor de simulacion de infraestructura");
                System.out.println("  Escenario actual: " + Ansi.BOLD + sim.scenario + Ansi.RESET
                        + (sim.ticksRemaining > 0 ? "  (restan " + sim.ticksRemaining + " ciclos)" : ""));
                System.out.println();
                System.out.println("  [1] Aumento de CPU");
                System.out.println("  [2] Aumento de memoria");
                System.out.println("  [3] Latencia de red elevada");
                System.out.println("  [4] Perdida de paquetes");
                System.out.println("  [5] Caida de nodo");
                System.out.println("  [6] Forzar recuperacion / reset");
                System.out.println("  [0] Volver");
                String c = prompt(Ansi.LIGHT_BLUE + "simulation> " + Ansi.RESET).trim();
                switch (c) {
                    case "1": sim.trigger(Scenario.CPU_SPIKE, 15); break;
                    case "2": sim.trigger(Scenario.MEMORY_PRESSURE, 15); break;
                    case "3": sim.trigger(Scenario.NET_DEGRADED, 15); break;
                    case "4": sim.trigger(Scenario.PACKET_LOSS, 15); break;
                    case "5": sim.trigger(Scenario.NODE_DOWN, 15); break;
                    case "6": sim.trigger(Scenario.NORMAL, 0); break;
                    case "0": return;
                    default: System.out.println(Ansi.RED + "Opcion no valida." + Ansi.RESET); Term.sleep(500);
                }
            }
        }

        // --- INCIDENTS ---
        void screenIncidents() throws IOException {
            while (true) {
                List<Incident> list = incidents.all();
                Term.clear();
                Ascii.header("INCIDENT CENTER -- " + list.size() + " incidentes");
                System.out.printf(Locale.ROOT, "  %-10s %-8s %-10s %-12s %-12s %s%n", "ID", "SEV", "HORA", "COMPONENTE", "ESTADO", "DESCRIPCION");
                DateTimeFormatter df = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
                for (Incident i : list) {
                    System.out.printf(Locale.ROOT, "  %-10s %-8s %-10s %-12s %-12s %s%n",
                            i.id, i.severity, df.format(i.time), i.component, i.status, i.description);
                }
                if (list.isEmpty()) System.out.println(Ansi.GRAY + "  No hay incidentes registrados." + Ansi.RESET);
                Ascii.footer("Comandos: investigar <ID> . resolver <ID> . salir");
                String cmd = prompt(Ansi.LIGHT_BLUE + "incidents> " + Ansi.RESET).trim();
                if (cmd.equalsIgnoreCase("salir") || cmd.equalsIgnoreCase("exit")) break;
                else if (cmd.startsWith("investigar ")) {
                    boolean ok = incidents.updateStatus(cmd.substring(11).trim(), IncStatus.INVESTIGATING);
                    if (!ok) { System.out.println(Ansi.RED + "ID no encontrado." + Ansi.RESET); Term.sleep(700); }
                } else if (cmd.startsWith("resolver ")) {
                    boolean ok = incidents.updateStatus(cmd.substring(9).trim(), IncStatus.RESOLVED);
                    if (!ok) { System.out.println(Ansi.RED + "ID no encontrado." + Ansi.RESET); Term.sleep(700); }
                }
            }
        }

        // --- REPORT ---
        void screenReport() throws IOException {
            String report = ReportService.build(lastSnapshot, lastProcs, lastIfaces, topology.all(), incidents.all(), tail(logs.all(), 15));
            Term.clear();
            Ascii.header("REPORT -- Informe de estado del sistema");
            System.out.println(report);
            Ascii.footer("Comandos: copiar (portapapeles) . salir");
            String cmd = prompt(Ansi.LIGHT_BLUE + "report> " + Ansi.RESET).trim();
            if (cmd.equalsIgnoreCase("copiar")) {
                boolean ok = copyToClipboard(report);
                System.out.println(ok ? Ansi.GREEN + "Informe copiado al portapapeles." + Ansi.RESET
                        : Ansi.RED + "No se pudo acceder al portapapeles en este entorno (sin display)." + Ansi.RESET);
                prompt(Ansi.GRAY + "Presiona ENTER para continuar..." + Ansi.RESET);
            }
        }

        private boolean copyToClipboard(String text) {
            try {
                java.awt.datatransfer.StringSelection sel = new java.awt.datatransfer.StringSelection(text);
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
                return true;
            } catch (Throwable t) {
                return false;
            }
        }

        private List<LogEntry> tail(List<LogEntry> list, int n) {
            int from = Math.max(0, list.size() - n);
            return new ArrayList<>(list.subList(from, list.size()));
        }

        // --- comandos de terminal (invocados por Terminal) ---
        void printStatusSummary() {
            Snapshot s = lastSnapshot;
            System.out.println("Estado: " + Ascii.badge(s.health));
            System.out.printf(Locale.ROOT, "CPU %.1f%% %s | RAM %.0f/%.0fMB %s | Disco %.1f/%.1fGB %s%n",
                    s.cpuPercent, tagStr(s.cpuOrigin), s.ramUsedMB, s.ramTotalMB, tagStr(s.ramOrigin), s.diskUsedGB, s.diskTotalGB, tagStr(s.diskOrigin));
            System.out.printf(Locale.ROOT, "Red In %.1f kb/s / Out %.1f kb/s %s | Latencia %.1fms %s%n",
                    s.netInKbps, s.netOutKbps, tagStr(s.netOrigin), s.latencyMs, tagStr(s.latencyOrigin));
            System.out.println("Uptime: " + ReportService.formatUptime(s.uptimeSeconds) + " | Procesos: " + lastProcs.size());
        }

        private String tagStr(Origin o) { return o == Origin.REAL ? "[REAL]" : "[SIM]"; }

        void printNetworkSummary() {
            for (Iface f : lastIfaces) {
                System.out.printf(Locale.ROOT, "%-10s %-15s %-4s RX %.1fkb/s TX %.1fkb/s Loss %.1f%%%n",
                        f.name, f.ip, f.up ? "UP" : "DOWN", f.rxKbps, f.txKbps, f.lossPercent);
            }
        }

        void printProcessSummary() {
            List<Proc> list = new ArrayList<>(lastProcs);
            list.sort((a, b) -> Double.compare(b.cpuPercent, a.cpuPercent));
            int n = 0;
            for (Proc p : list) {
                if (n++ >= 10) break;
                System.out.printf(Locale.ROOT, "PID %-6d %-20s CPU %5.1f%% RAM %6.0fMB %s%n", p.pid, p.name, p.cpuPercent, p.ramMB, p.state);
            }
        }

        void printLogsTail(int n) {
            List<LogEntry> all = logs.all();
            int from = Math.max(0, all.size() - n);
            for (int i = from; i < all.size(); i++) System.out.println(LogService.format(all.get(i)));
        }

        void runScan() {
            System.out.println(Ansi.LIGHT_BLUE + "Ejecutando escaneo de infraestructura..." + Ansi.RESET);
            for (Node node : topology.all()) {
                Term.sleep(150);
                String colored = node.status == NodeStatus.ONLINE ? Ansi.GREEN + "OK" + Ansi.RESET
                        : node.status == NodeStatus.DEGRADED ? Ansi.YELLOW + "DEGRADADO" + Ansi.RESET
                        : Ansi.RED + "OFFLINE" + Ansi.RESET;
                System.out.println("  [" + node.type + "] " + node.name + " ... " + colored);
            }
            logs.info("Escaneo de infraestructura completado.");
            System.out.println(Ansi.GREEN + "Escaneo completado." + Ansi.RESET);
        }

        void printIncidentSummary(String[] args) {
            if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
                for (Incident i : incidents.all()) {
                    System.out.printf("%s [%s] %-10s %s - %s%n", i.id, i.severity, i.component, i.status, i.description);
                }
                return;
            }
            if (args.length >= 2 && (args[0].equalsIgnoreCase("resolve") || args[0].equalsIgnoreCase("investigate"))) {
                IncStatus st = args[0].equalsIgnoreCase("resolve") ? IncStatus.RESOLVED : IncStatus.INVESTIGATING;
                boolean ok = incidents.updateStatus(args[1], st);
                System.out.println(ok ? Ansi.GREEN + "Incidente actualizado." + Ansi.RESET : Ansi.RED + "ID de incidente no encontrado." + Ansi.RESET);
                return;
            }
            System.out.println(Ansi.GRAY + "Uso: incident list | incident resolve <ID> | incident investigate <ID>" + Ansi.RESET);
        }

        void generateAndShowReport() {
            String report = ReportService.build(lastSnapshot, lastProcs, lastIfaces, topology.all(), incidents.all(), tail(logs.all(), 12));
            System.out.println(report);
        }

        void runSimulateCommand(String[] args) {
            if (args.length == 0) {
                System.out.println(Ansi.GRAY + "Uso: simulate <cpu|memoria|red|paquetes|nodo|reset>" + Ansi.RESET);
                return;
            }
            String s = args[0].toLowerCase(Locale.ROOT);
            switch (s) {
                case "cpu": sim.trigger(Scenario.CPU_SPIKE, 15); break;
                case "memoria": sim.trigger(Scenario.MEMORY_PRESSURE, 15); break;
                case "red": sim.trigger(Scenario.NET_DEGRADED, 15); break;
                case "paquetes": sim.trigger(Scenario.PACKET_LOSS, 15); break;
                case "nodo": sim.trigger(Scenario.NODE_DOWN, 15); break;
                case "reset": sim.trigger(Scenario.NORMAL, 0); break;
                default: System.out.println(Ansi.RED + "Escenario desconocido." + Ansi.RESET); return;
            }
            System.out.println(Ansi.YELLOW + "Escenario de simulacion activado: " + s + Ansi.RESET);
        }

        // --- vista "en vivo" con redibujado periodico (ENTER para salir) ---
        void liveView(String title, Runnable body) {
            final AtomicBoolean stop = new AtomicBoolean(false);
            Thread waiter = new Thread(() -> {
                try { stdin.readLine(); } catch (IOException ignored) { }
                stop.set(true);
            });
            waiter.setDaemon(true);
            waiter.start();
            while (!stop.get() && running) {
                Term.clear();
                Ascii.header("SENTINELLAB -- " + title + "  (en vivo)");
                body.run();
                Ascii.footer("Presiona ENTER para volver al menu principal.");
                Term.sleep(1000);
            }
        }
    }

    // =====================================================================
    // MAIN
    // =====================================================================

    public static void main(String[] args) {
        try {
            new App().start();
        } catch (Exception e) {
            System.err.println("Error fatal en SentinelLab: " + e);
            e.printStackTrace();
        }
    }
}