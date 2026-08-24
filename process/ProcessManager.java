package process;

import java.util.*;

public class ProcessManager {
    private final List<OSProcess> processes = Collections.synchronizedList(new ArrayList<>());
    private final Scheduler scheduler;

    public ProcessManager() {
        scheduler = new Scheduler(processes);
        OSProcess kernel = new OSProcess("kernel_main", 0, "SYSTEM");
        kernel.setState("RUNNING");
        processes.add(kernel);
    }

    public OSProcess createProcess(String name, int priority, String owner) {
        OSProcess p = new OSProcess(name, priority, owner);
        processes.add(p);
        scheduler.schedule();
        return p;
    }

    public void terminateProcess(OSProcess p) {
        p.terminate();
        scheduler.schedule();
    }

    public void blockProcess(OSProcess p) {
        p.setState("BLOCKED");
        scheduler.schedule();
    }

    public void unblockProcess(OSProcess p) {
        p.setState("READY");
        scheduler.schedule();
    }

    public List<OSProcess> getAllProcesses()    { return Collections.unmodifiableList(processes); }
    public int             getProcessCount()   { return processes.size(); }

    public List<OSProcess> getActiveProcesses() {
        List<OSProcess> a = new ArrayList<>();
        for (OSProcess p : processes)
            if (!p.getState().equals("TERMINATED")) a.add(p);
        return a;
    }
}
