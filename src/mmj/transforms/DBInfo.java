package mmj.transforms;

public abstract class DBInfo {
    protected boolean dbg;

    /** This field is true if this object was initialized */
    private boolean isInit = false;

    /** For the debug and error output */
    protected TrOutput output;

    protected DBInfo(final TrOutput output, final boolean dbg) {
        this.output = output;
        this.dbg = dbg;
        isInit = true;
    }

    public boolean isInit() {
        return isInit;
    }
}