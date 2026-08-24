package memory;

public class PageEntry {
    private int pageNumber;
    private int frameNumber;
    private String accountId;
    private boolean valid;
    private boolean dirty;

    public PageEntry(int pageNumber, int frameNumber, String accountId) {
        this.pageNumber  = pageNumber;
        this.frameNumber = frameNumber;
        this.accountId   = accountId;
        this.valid       = true;
        this.dirty       = false;
    }

    public void markDirty()   { this.dirty = true; }
    public void markClean()   { this.dirty = false; }
    public void invalidate()  { this.valid = false; }
    public void validate()    { this.valid = true; }

    public int     getPageNumber()   { return pageNumber; }
    public int     getFrameNumber()  { return frameNumber; }
    public String  getAccountId()    { return accountId; }
    public boolean isValid()         { return valid; }
    public boolean isDirty()         { return dirty; }
}
