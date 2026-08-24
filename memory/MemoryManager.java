package memory;

import java.util.*;

public class MemoryManager {
    private static final int TOTAL_FRAMES = 16;
    private final Map<String, PageEntry> pageTable = new LinkedHashMap<>();
    private int nextPage = 0, nextFrame = 1, usedFrames = 1;

    public synchronized PageEntry allocate(String accountId) {
        if (pageTable.containsKey(accountId)) return pageTable.get(accountId);
        if (usedFrames >= TOTAL_FRAMES) swapOut();
        PageEntry pe = new PageEntry(nextPage++, nextFrame++, accountId);
        pageTable.put(accountId, pe);
        usedFrames++;
        return pe;
    }

    public synchronized PageEntry access(String accountId) {
        PageEntry pe = pageTable.get(accountId);
        if (pe == null || !pe.isValid()) return allocate(accountId);
        return pe;
    }

    public synchronized void markDirty(String accountId) {
        PageEntry pe = pageTable.get(accountId);
        if (pe != null) pe.markDirty();
    }

    public synchronized void markClean(String accountId) {
        PageEntry pe = pageTable.get(accountId);
        if (pe != null) pe.markClean();
    }

    public synchronized void free(String accountId) {
        PageEntry pe = pageTable.remove(accountId);
        if (pe != null) { usedFrames--; pe.invalidate(); }
    }

    private void swapOut() {
        Iterator<Map.Entry<String, PageEntry>> it = pageTable.entrySet().iterator();
        if (it.hasNext()) {
            Map.Entry<String, PageEntry> oldest = it.next();
            oldest.getValue().invalidate();
            it.remove();
            usedFrames--;
        }
    }

    public synchronized List<PageEntry> getAllPages() { return new ArrayList<>(pageTable.values()); }
    public int getUsedFrames()         { return usedFrames; }
    public int getTotalFrames()        { return TOTAL_FRAMES; }
    public int getMemoryUsagePercent() { return (usedFrames * 100) / TOTAL_FRAMES; }
    public boolean wasPageFault(String id) { return !pageTable.containsKey(id); }
}
