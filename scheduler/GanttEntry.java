package scheduler;

public class GanttEntry {
    private String processName;
    private int    startTime;
    private int    endTime;

    public GanttEntry(String processName, int startTime, int endTime) {
        this.processName = processName;
        this.startTime   = startTime;
        this.endTime     = endTime;
    }

    public String getProcessName() { return processName; }
    public int    getStartTime()   { return startTime; }
    public int    getEndTime()     { return endTime; }
    public int    getDuration()    { return endTime - startTime; }
}
