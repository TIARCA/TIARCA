package io.mrarm.irc.lua;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.atomic.AtomicInteger;

/** Host JNI only: real paced arrivals and bounded synthetic queue, not Android Binder/IRC. */
public final class HostLuaStressTest {
    private static final String ID = "16a6f13c-2b6d-4e0e-a16b-6f37e40be6d0";
    private static final String SERVER = "2598f719-dfb6-4bad-a69c-ff78cecb9315";
    private static final int MEMBERS = 1000, ACTIVE = 500, CAPACITY = 32;
    private static final byte[][] EMPTY = new byte[0][];
    private static final int[] COUNTS = new int[0];
    private static final long T = 1794400000L;
    private static long observations;
    private static native long fullGc();
    private static native long allocationPeak(boolean reset);
    private static final long VM_LIMIT = 2L * 1024 * 1024;
    private static byte[] b(String s) { return NativeLuaRuntime.utf8(s); }
    private static String s(byte[] b) { return NativeLuaRuntime.decode(b); }
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static final class Member {
        final byte[] nick;
        final byte[][] sender;
        Member(int i) { this(i, false); }
        Member(int i, boolean changed) {
            nick = b((changed ? "Alias" : "User") + i);
            sender = new byte[][]{b("ident" + i),
                    b("simosnap-" + (changed ? "new-" : "") + i + ".vodafone.it"),
                    b(i % 5 == 0 ? "+" : ""), b((changed ? "new-account" : "account") + i)};
        }
    }
    private static final class Arrival {
        final Member member;
        final long due;
        Arrival(Member member, long due) { this.member = member; this.due = due; }
    }
    private static byte[][] run(String kind, byte[] nick, String text, long time,
                               byte[][] sender, byte[][] storage, long revision) {
        byte[][] result = NativeLuaRuntime.dispatchWithMetadata(ID, kind, b(SERVER),
                b("#room"), nick, b(text), b("normal"), time, EMPTY, COUNTS,
                false, EMPTY, false, true, storage, true, sender, revision,
                b("message".equals(kind) ? SERVER + ":" + (++observations) : ""), EMPTY, null);
        check(result != null && result.length >= 1 && "OK".equals(s(result[0])),
                "Runtime failed: " + kind + " " + s(nick) + " " + (result == null ? "null" : s(result[0])));
        return result;
    }
    private static byte[][] configure() {
        byte[][] reply = run("command", b("me"), "mod canali add #room", T, EMPTY, EMPTY, 1);
        Map<String, String> state = new LinkedHashMap<>();
        for (int i=1;i<reply.length;i+=3)
            if ("store".equals(s(reply[i]))) state.put(s(reply[i+1]), s(reply[i+2]));
        check(state.containsKey("v4.cfg"), "V4 did not persist channel configuration");
        byte[][] pairs = new byte[state.size()*2][];
        int n=0;
        for (Map.Entry<String,String> e:state.entrySet()) {
            pairs[n++]=b(e.getKey()); pairs[n++]=b(e.getValue());
        }
        return pairs;
    }
    private static double percentile(long[] values, int n, double p) {
        if (n == 0) return Double.NaN;
        Arrays.sort(values,0,n);
        return values[Math.max(0,(int)Math.ceil(p*n)-1)]/1e6;
    }
    private static void scenario(byte[] source, Member[] members, int rate) throws Exception {
        NativeLuaRuntime.clear();
        allocationPeak(true);
        check("OK".equals(s(NativeLuaRuntime.load(ID, source))), "V4 failed to load");
        check((NativeLuaRuntime.eventMask(ID)&(1<<20)) != 0,"V4 did not opt in");
        byte[][] stored = configure();
        // Pre-seed one announcement from each of the 500 active members.
        for (int i=0;i<ACTIVE;i++)
            run("message",members[MEMBERS-ACTIVE+i].nick,"cerco ragazza",T+1,
                    members[MEMBERS-ACTIVE+i].sender,stored,2);
        ArrayBlockingQueue<Arrival> queue = new ArrayBlockingQueue<>(CAPACITY);
        AtomicInteger dropped = new AtomicInteger(), high = new AtomicInteger();
        long period = 1_000_000_000L/rate;
        long start = System.nanoTime()+100_000_000L;
        Thread producer = new Thread(() -> {
            for (int i=0;i<ACTIVE;i++) {
                long due=start+i*period;
                long remaining;
                while ((remaining=due-System.nanoTime())>0) LockSupport.parkNanos(remaining);
                if (!queue.offer(new Arrival(members[MEMBERS-ACTIVE+i],due))) dropped.incrementAndGet();
                else high.accumulateAndGet(queue.size(),Math::max);
            }
        },"lua-stress-arrivals");
        producer.setDaemon(true);
        producer.start();
        long[] latency=new long[ACTIVE], execution=new long[ACTIVE];
        int processed=0, detected=0;
        long maxBytes=NativeLuaRuntime.allocatedBytes();
        while (producer.isAlive() || !queue.isEmpty()) {
            Arrival arrival=queue.poll(100,TimeUnit.MILLISECONDS);
            if (arrival==null) continue;
            long before=System.nanoTime();
            byte[][] result=run("message",arrival.member.nick,"cerco ragazza",
                    T+2+(arrival.due-start)/1_000_000_000L,arrival.member.sender,stored,2);
            long end=System.nanoTime();
            latency[processed]=end-arrival.due;
            execution[processed]=end-before;
            processed++;
            boolean found=false;
            for(int i=1;i<result.length;i+=3) {
                String kind=s(result[i]);
                check(!"query".equals(kind) && !"store".equals(kind) && !"erase".equals(kind),
                        "Unexpected query/write during monitor message");
                if("log".equals(kind) && s(result[i+2]).startsWith("AutoModV4 ")) found=true;
            }
            if(found) detected++;
            maxBytes=Math.max(maxBytes,NativeLuaRuntime.allocatedBytes());
        }
        producer.join();
        double seconds=(System.nanoTime()-start)/1e9;
        check(processed+dropped.get()==ACTIVE,"Arrival accounting mismatch");
        check(detected==processed,"V4 missed a seeded announcement");
        System.out.printf(Locale.ROOT,
            "host rate=%d/s members=%d active=%d offered=%d processed=%d dropped=%d detections=%d duration=%.2fs throughput=%.2f/s queue_sample_max=%d latency_p95=%.3fms latency_p99=%.3fms JNI_p95=%.3fms lua_peak_sample=%dB%n",
            rate,MEMBERS,ACTIVE,ACTIVE,processed,dropped.get(),detected,seconds,processed/seconds,
            high.get(),percentile(latency,processed,.95),percentile(latency,processed,.99),
            percentile(execution,processed,.95),maxBytes);
        check(processed>0,"No events processed");
        System.out.printf(Locale.ROOT,
                "host rate=%d/s allocator_peak=%dB after_full_gc=%dB%n",
                rate, allocationPeak(false), fullGc());
        NativeLuaRuntime.clear();
        check(NativeLuaRuntime.allocatedBytes()==0,"Lua allocations remain after clear");
    }
    private static int detections(byte[][] response, String engine) {
        int count=0;
        for (int i=1; i<response.length; i+=3) {
            String kind=s(response[i]), value=s(response[i+2]);
            check(!"query".equals(kind) && !"store".equals(kind) && !"erase".equals(kind),
                    "Unexpected query/write in mixed monitor workload");
            if ("log".equals(kind) && value.startsWith("AutoModV4 ") &&
                    value.contains("motore "+engine)) count++;
        }
        return count;
    }
    private static void measure(String phase) {
        long before=NativeLuaRuntime.allocatedBytes();
        long peak=allocationPeak(false);
        long retained=fullGc();
        System.out.printf(Locale.ROOT,
                "memory phase=%s before_gc=%dB retained=%dB true_peak=%dB retained_pct=%.2f%% target=75%% %n",
                phase,before,retained,peak,100.0*retained/VM_LIMIT);
        check(retained<VM_LIMIT,"Live heap exhausted in "+phase);
        allocationPeak(true);
    }
    /** Same native sandbox, no synthetic Lua allocation model. Five distinct
     * repetition groups, B ads, quiet joins, alias changes and eviction churn. */
    private static void mixed(byte[] source, Member[] members) {
        NativeLuaRuntime.clear();
        allocationPeak(true);
        check("OK".equals(s(NativeLuaRuntime.load(ID,source))),"Mixed V4 load");
        byte[][] stored=configure();
        measure("configured");
        for(int i=0;i<MEMBERS;i++)
            run("join",members[i].nick,"",T+1+i/100,members[i].sender,stored,2);
        measure("1000-joins");
        String[] texts={"il sole illumina questa strada oggi",
                "mentre cucino preparo delle patate",
                "domani vado al supermercato presto",
                "la montagna sembra lontana stasera",
                "ho appena finito il lavoro"};
        int first=MEMBERS-ACTIVE;
        for(int k=0;k<texts.length;k++)
            for(int i=first;i<MEMBERS;i++) {
                String text=texts[k]+" numero "+i;
                check(detections(run("message",members[i].nick,text,T+20+k,
                        members[i].sender,stored,2),"A")==0,"First occurrence triggered");
            }
        measure("500-users-five-A-groups");
        // Quiet join churn must not evict the writers' active repetition groups.
        for(int i=MEMBERS;i<MEMBERS+1000;i++) {
            Member quiet=new Member(i);
            run("join",quiet.nick,"",T+30+i/100,quiet.sender,stored,2);
        }
        measure("1000-more-joins");
        Member[] aliases=new Member[ACTIVE];
        for(int i=first;i<MEMBERS;i++) {
            Member changed=new Member(i,true);
            aliases[i-first]=changed;
            run("nick",members[i].nick,s(changed.nick),T+55,members[i].sender,stored,2);
        }
        measure("500-nick-changes");
        // Reconnect with a new account and full host; ident/domain bridges the change.
        int a=0;
        for(int round=0;round<2;round++)
            for(int k=0;k<texts.length;k++)
                for(int i=first;i<MEMBERS;i++) {
                    Member m=aliases[i-first];
                    a+=detections(run("message",m.nick,texts[k]+" numero "+i,T+60+round*10+k,
                            m.sender,stored,2),"A");
                }
        check(a==ACTIVE*texts.length,"Five nonconsecutive A groups retained: "+a);
        measure("500-aliased-users-A-detections");
        int ads=0;
        for(int round=0;round<2;round++)
            for(Member m:aliases)
                ads+=detections(run("message",m.nick,round==0?"F per privato?":"una tipa per pvt?",
                        T+90+round,m.sender,stored,2),"B");
        check(ads==ACTIVE,"Semantic B announcements retained: "+ads);
        measure("A-and-B-together");
        run("timer",b(""),"",T+1000,EMPTY,stored,2);
        measure("after-window");
        run("timer",b(""),"",T+90000,EMPTY,stored,2);
        measure("after-identity-ttl");
        System.out.printf(Locale.ROOT,"mixed verified A=%d B=%d, no runtime errors%n",a,ads);
        NativeLuaRuntime.clear();
        check(NativeLuaRuntime.allocatedBytes()==0,"Mixed VM leaked allocator bytes");
    }
    public static void main(String[] args) throws Exception {
        byte[] source=Files.readAllBytes(Path.of("docs/lua-examples/automod_v4.lua"));
        Member[] members=new Member[MEMBERS];
        for(int i=0;i<MEMBERS;i++) members[i]=new Member(i);
        mixed(source,members);
        for(int rate:new int[]{5,10,25,50}) scenario(source,members,rate);
    }
}
