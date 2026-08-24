package scheduler;

import java.util.*;

public class CPUScheduler {

    // ════════════════════════════════════════════════════════════
    //  FCFS — First Come First Served (Non-Preemptive)
    // ════════════════════════════════════════════════════════════
    public static List<GanttEntry> fcfs(List<SchedulerProcess> processes) {
        List<GanttEntry> gantt = new ArrayList<>();

        // Sort by arrival time
        List<SchedulerProcess> sorted = new ArrayList<>(processes);
        sorted.sort(Comparator.comparingInt(SchedulerProcess::getArrivalTime));

        int currentTime = 0;

        for (SchedulerProcess p : sorted) {
            // CPU idle gap
            if (currentTime < p.getArrivalTime()) {
                gantt.add(new GanttEntry("IDLE", currentTime, p.getArrivalTime()));
                currentTime = p.getArrivalTime();
            }
            p.setStartTime(currentTime);
            int end = currentTime + p.getBurstTime();
            gantt.add(new GanttEntry(p.getName(), currentTime, end));
            p.setFinishTime(end);
            p.setWaitingTime(p.getStartTime() - p.getArrivalTime());
            currentTime = end;
        }
        return gantt;
    }

    // ════════════════════════════════════════════════════════════
    //  ROUND ROBIN — Preemptive with Time Quantum
    // ════════════════════════════════════════════════════════════
    public static List<GanttEntry> roundRobin(List<SchedulerProcess> processes, int quantum) {
        List<GanttEntry> gantt  = new ArrayList<>();
        Queue<SchedulerProcess> readyQueue = new LinkedList<>();

        // Deep copy so originals are not mutated
        List<SchedulerProcess> copy = deepCopy(processes);
        copy.sort(Comparator.comparingInt(SchedulerProcess::getArrivalTime));

        int currentTime = 0;
        int idx = 0; // index into sorted arrival list

        while (true) {
            // Enqueue all processes that have arrived by currentTime
            while (idx < copy.size() && copy.get(idx).getArrivalTime() <= currentTime) {
                readyQueue.add(copy.get(idx++));
            }

            if (readyQueue.isEmpty()) {
                if (idx < copy.size()) {
                    // CPU idle until next arrival
                    int nextArrival = copy.get(idx).getArrivalTime();
                    gantt.add(new GanttEntry("IDLE", currentTime, nextArrival));
                    currentTime = nextArrival;
                    continue;
                } else {
                    break; // all done
                }
            }

            SchedulerProcess p = readyQueue.poll();
            p.setStartTime(currentTime);

            int execTime = Math.min(quantum, p.getRemainingTime());
            int endTime  = currentTime + execTime;

            gantt.add(new GanttEntry(p.getName(), currentTime, endTime));
            p.setRemainingTime(p.getRemainingTime() - execTime);
            currentTime = endTime;

            // Enqueue newly arrived processes during this slice
            while (idx < copy.size() && copy.get(idx).getArrivalTime() <= currentTime) {
                readyQueue.add(copy.get(idx++));
            }

            if (!p.isDone()) {
                readyQueue.add(p); // re-enqueue for next turn
            } else {
                p.setFinishTime(currentTime);
                p.setWaitingTime(p.getTurnaroundTime() - p.getBurstTime());
                // Push result back to original list
                syncBack(processes, p);
            }
        }
        return gantt;
    }

    // ════════════════════════════════════════════════════════════
    //  PRIORITY — Non-Preemptive (lower number = higher priority)
    // ════════════════════════════════════════════════════════════
    public static List<GanttEntry> priority(List<SchedulerProcess> processes) {
        List<GanttEntry> gantt = new ArrayList<>();
        List<SchedulerProcess> remaining = new ArrayList<>(processes);
        remaining.sort(Comparator.comparingInt(SchedulerProcess::getArrivalTime));

        int currentTime = 0;

        while (!remaining.isEmpty()) {
            // Find highest priority process that has arrived
            final int ct = currentTime;
            SchedulerProcess best = remaining.stream()
                .filter(p -> p.getArrivalTime() <= ct)
                .min(Comparator.comparingInt(SchedulerProcess::getPriority))
                .orElse(null);

            if (best == null) {
                // CPU idle
                int nextArrival = remaining.get(0).getArrivalTime();
                gantt.add(new GanttEntry("IDLE", currentTime, nextArrival));
                currentTime = nextArrival;
                continue;
            }

            best.setStartTime(currentTime);
            int end = currentTime + best.getBurstTime();
            gantt.add(new GanttEntry(best.getName(), currentTime, end));
            best.setFinishTime(end);
            best.setWaitingTime(best.getStartTime() - best.getArrivalTime());
            currentTime = end;
            remaining.remove(best);
        }
        return gantt;
    }

    // ════════════════════════════════════════════════════════════
    //  PREEMPTIVE PRIORITY (SRT-style, preempt on better priority)
    // ════════════════════════════════════════════════════════════
    public static List<GanttEntry> preemptivePriority(List<SchedulerProcess> processes) {
        List<GanttEntry> gantt = new ArrayList<>();
        List<SchedulerProcess> copy = deepCopy(processes);
        copy.sort(Comparator.comparingInt(SchedulerProcess::getArrivalTime));

        int currentTime = 0;
        int totalBurst  = copy.stream().mapToInt(SchedulerProcess::getBurstTime).sum();
        int endTime     = copy.stream().mapToInt(p -> p.getArrivalTime() + p.getBurstTime()).max().orElse(0);

        while (currentTime <= endTime + totalBurst) {
            final int ct = currentTime;
            SchedulerProcess best = copy.stream()
                .filter(p -> p.getArrivalTime() <= ct && !p.isDone())
                .min(Comparator.comparingInt(SchedulerProcess::getPriority))
                .orElse(null);

            if (best == null) {
                boolean anyLeft = copy.stream().anyMatch(p -> !p.isDone());
                if (!anyLeft) break;
                gantt.add(new GanttEntry("IDLE", currentTime, currentTime + 1));
                currentTime++;
                continue;
            }

            best.setStartTime(currentTime);
            best.setRemainingTime(best.getRemainingTime() - 1);
            // Merge consecutive same-process entries
            if (!gantt.isEmpty() && gantt.get(gantt.size()-1).getProcessName().equals(best.getName())) {
                GanttEntry last = gantt.remove(gantt.size()-1);
                gantt.add(new GanttEntry(best.getName(), last.getStartTime(), currentTime + 1));
            } else {
                gantt.add(new GanttEntry(best.getName(), currentTime, currentTime + 1));
            }

            if (best.isDone()) {
                best.setFinishTime(currentTime + 1);
                best.setWaitingTime(best.getTurnaroundTime() - best.getBurstTime());
                syncBack(processes, best);
            }
            currentTime++;
        }
        return gantt;
    }

    // ── Helpers ──────────────────────────────────────────────────
    private static List<SchedulerProcess> deepCopy(List<SchedulerProcess> src) {
        List<SchedulerProcess> copy = new ArrayList<>();
        for (SchedulerProcess p : src)
            copy.add(new SchedulerProcess(p.getName(), p.getArrivalTime(), p.getBurstTime(), p.getPriority()));
        return copy;
    }

    private static void syncBack(List<SchedulerProcess> originals, SchedulerProcess done) {
        for (SchedulerProcess orig : originals) {
            if (orig.getName().equals(done.getName())) {
                orig.setFinishTime(done.getFinishTime());
                orig.setWaitingTime(done.getWaitingTime());
                break;
            }
        }
    }
}
