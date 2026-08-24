package process;

import java.util.List;

public class Scheduler {
    private List<OSProcess> processes;
    private int timeQuantum = 100;
    private int currentIndex = 0;

    public Scheduler(List<OSProcess> processes) {
        this.processes = processes;
    }

    public void schedule() {
        int tried = 0;
        while (tried < processes.size()) {
            currentIndex = (currentIndex + 1) % processes.size();
            OSProcess p = processes.get(currentIndex);
            if (p.getState().equals("READY")) {
                p.setState("RUNNING");
                p.addCpuTime(timeQuantum);
                return;
            }
            tried++;
        }
    }

    public void simulateContextSwitch(OSProcess from, OSProcess to) {
        from.setState("READY");
        to.setState("RUNNING");
    }
}
