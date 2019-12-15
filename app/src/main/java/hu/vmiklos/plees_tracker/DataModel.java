package hu.vmiklos.plees_tracker;

import java.util.Date;

public class DataModel
{
    private static final DataModel sDataModel = new DataModel();

    private Date mStart = null;
    private Date mStop = null;

    public static DataModel getDataModel() { return sDataModel; }

    private DataModel() {}

    void setStart(Date start) { mStart = start; }

    Date getStart() { return mStart; }

    void setStop(Date stop) { mStop = stop; }

    Date getStop() { return mStop; }
}
