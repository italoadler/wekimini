/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package wekimini.learning.dtw;

import com.timeseries.TimeSeries;
import com.timeseries.TimeSeriesPoint;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import wekimini.ConnectsInputsToOutputs;
import wekimini.DtwLearningManager;
import wekimini.Wekinator;

/**
 *
 * @author rebecca
 */
public class DtwData {
    
    private final transient List<DtwDataListener> dataListeners = new LinkedList<>();
    private transient long currentTime = 0;
    private transient TimeSeries currentTimeSeries;
    private final int numGestures;
    private transient int currentClass;
    private transient final Wekinator w;
    private final ArrayList<LinkedList<DtwExample>> allExamples;
    private final HashMap<Integer, LinkedList<DtwExample>> exampleListForIds;
    private final HashMap<Integer, DtwExample> examplesForIds;
    private int minSizeInExamples = Integer.MAX_VALUE;
    private int maxSizeInExamples = 0;
    private int maxLengthToRetainDuringRun = Integer.MAX_VALUE;
    private static final Logger logger = Logger.getLogger(DtwData.class.getName());
    //private int numTotalExamples = 0;
    
    private int numTotalExamples = 0;
    private final int numInputs;
    private int numActiveInputs;
    
    public static final String PROP_NUMTOTALEXAMPLES = "numTotalExamples";

    /**
     * Get the value of numTotalExamples
     *
     * @return the value of numTotalExamples
     */
    public int getNumTotalExamples() {
        return numTotalExamples;
    }

    /**
     * Set the value of numTotalExamples
     *
     * @param numTotalExamples new value of numTotalExamples
     */
    public void setNumTotalExamples(int numTotalExamples) {
        int oldNumTotalExamples = this.numTotalExamples;
        this.numTotalExamples = numTotalExamples;
        propertyChangeSupport.firePropertyChange(PROP_NUMTOTALEXAMPLES, oldNumTotalExamples, numTotalExamples);
    }
    
    private transient final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    /**
     * Add PropertyChangeListener.
     *
     * @param listener
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    /**
     * Remove PropertyChangeListener.
     *
     * @param listener
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }
    
    public DtwData(int numGestures, DtwLearningManager learningManager, Wekinator w) {
        this.numGestures = numGestures;
        this.w = w;
        this.numInputs = w.getInputManager().getNumInputs();
        this.numActiveInputs = numInputs; //XXX if learning manager has connection info here, need to use it now
        allExamples = new ArrayList<>();
        for (int i = 0; i < numGestures; i++) {
            allExamples.add(new LinkedList<DtwExample>());
        }
        exampleListForIds = new HashMap<>();
        examplesForIds = new HashMap<>();
        learningManager.addConnectionsListener(new ConnectsInputsToOutputs.InputOutputConnectionsListener() {

            @Override
            public void newConnectionMatrix(boolean[][] connections) {
                connectionsChanged();
            }
        });
    }
    
    private void connectionsChanged() {
        //XXX TODO feature selection
        //Change numActiveInputs
        //Continue putting all inputs into timeseires, but modify timeseires before distance calculation
        // (or change distance function)
    }
    
    public int getMaxLengthToRetainDuringRun() {
        return maxLengthToRetainDuringRun;
    }
    
    public void setMaxLengthToRetainDuringRun(int maxLengthToRetainDuringRun) {
        this.maxLengthToRetainDuringRun = maxLengthToRetainDuringRun;
    }
    
    public void delete(int id) {
        List<DtwExample> matchingList = exampleListForIds.get(id);
        if (matchingList == null) {
            logger.log(Level.WARNING, "ID {0} not found in exampleListForIds!", id);
            return;
        }
        DtwExample matchingExample = examplesForIds.get(id);
        if (matchingExample == null) {
            logger.log(Level.WARNING, "ID {0} not found in examplesForIds", id);
            return;
        }
        boolean removed = matchingList.remove(matchingExample);
        if (!removed) {
            logger.log(Level.WARNING, "ID {0} not found in examplesForIds", id);
            return;
        }
        
        if (matchingExample.getTimeSeries().size() == minSizeInExamples) {
            updateMinSize();
        }
        if (matchingExample.getTimeSeries().size() == maxSizeInExamples) {
            updateMaxSize();
        }
        
        exampleListForIds.remove(id);
        examplesForIds.remove(id);
        
        int whichClass = allExamples.indexOf(matchingList);
        
        setNumTotalExamples(numTotalExamples - 1);
        
        notifyExamplesChangedListeners(whichClass, matchingList.size());
        notifyExampleDeletedListeners(whichClass);
    }
    
    public void deleteAll() {
        exampleListForIds.clear();
        examplesForIds.clear();
        allExamples.clear();
        for (int i = 0; i < numGestures; i++) {
            allExamples.add(new LinkedList<DtwExample>());
        }
        minSizeInExamples = Integer.MAX_VALUE;
        maxSizeInExamples = 0;
        setNumTotalExamples(0);
        
        for (int i = 0; i < numGestures; i++) {
            notifyExamplesChangedListeners(i, 0);
        }
        notifyAllExamplesDeletedListeners();
    }
    
    protected void startRecording(int currentClass) {
        currentTimeSeries = new TimeSeries(numActiveInputs);
        currentTime = 0;
        this.currentClass = currentClass;
    }
    
    protected void stopRecording() {
        addTrainingExample();
    }
    
    public void addTrainingVector(double[] d) {
        TimeSeriesPoint p = new TimeSeriesPoint(d);
        currentTimeSeries.addLast(currentTime, p);
        currentTime++;
    }    
    
    public void addRunningVector(double[] d) {
        TimeSeriesPoint p = new TimeSeriesPoint(d);
        currentTimeSeries.addLast(currentTime, p);
        
        while (currentTimeSeries.size() > maxLengthToRetainDuringRun) {
            currentTimeSeries.removeFirst();
        }
        
        currentTime++;
    }
    
    public void addTrainingExample() {
        if (currentTimeSeries.size() == 0) {
            w.getStatusUpdateCenter().warn(this, "Could not add new example: length was 0");
            return;
        }
        LinkedList<DtwExample> list = allExamples.get(currentClass);
        int id = DtwExample.generateNextID();
        DtwExample ex = new DtwExample(currentTimeSeries, id);
        list.add(ex);
        if (currentTimeSeries.size() < minSizeInExamples) {
            minSizeInExamples = currentTimeSeries.size();
        }
        if (currentTimeSeries.size() > maxSizeInExamples) {
            maxSizeInExamples = currentTimeSeries.size();
        }
        exampleListForIds.put(id, list);
        examplesForIds.put(id, ex);
        setNumTotalExamples(numTotalExamples + 1);
        notifyExamplesChangedListeners(currentClass, list.size());
        notifyExampleAddedListeners(currentClass);
    }
    
    public int getNumExamplesForGesture(int gesture) {
        return allExamples.get(gesture).size();
    }
    
    public List<LinkedList<DtwExample>> getAllExamples() {
        return allExamples;
    }
    
    public List<DtwExample> getExamplesForGesture(int gesture) {
        return allExamples.get(gesture);
    }
    
    public void startRunning() {
        currentTime = 0;
        currentTimeSeries = new TimeSeries(numActiveInputs);
    }
    
    public void stopRunning() {
        
    }
    
    public void dumpExamplesForGesture(int whichGesture) {
        List<DtwExample> examples = allExamples.get(whichGesture);
        System.out.println(examples.size()  + " EXAMPLES FOR GESTURE " + whichGesture +":");
        int i = 0;
        for (DtwExample ex : examples) {
            System.out.println("   " + i++ + " - length: " + ex.getTimeSeries().numOfPts());
        }
    }
    
    public String getSummaryStringForGesture(int whichGesture) {
        List<DtwExample> examples = allExamples.get(whichGesture);
        StringBuilder sb = new StringBuilder();
        sb.append(examples.size()).append(" examples:\n");
        int i = 0;
        for (DtwExample ex : examples) {
            sb.append("Example ").append(i++).append(": length=").append(ex.getTimeSeries().numOfPts());
        }
        return sb.toString();
    }
    
    public void dumpAllExamples() {
        for (int i = 0; i < numGestures; i++) {
            dumpExamplesForGesture(i);
        }
    }
    
    public int getMinSizeInExamples() {
        return minSizeInExamples;
    }
    
    public int getMaxSizeInExamples() {
        return maxSizeInExamples;
    }
    
    public void addDataListener(DtwDataListener listener) {
        if (listener != null) {
            dataListeners.add(listener);
        }
    }
    
    public void removeDataListener(DtwDataListener listener) {
        if (listener != null) {
            dataListeners.remove(listener);
        }
    }
    
    private void updateMinSize() {
        int min = Integer.MAX_VALUE;
        for (List<DtwExample> list : allExamples) {
            for (DtwExample ex : list) {
                if (ex.getTimeSeries().size() < min) {
                    min = ex.getTimeSeries().size();
                }
            }            
        }
        minSizeInExamples = min;
    }
    
    private void updateMaxSize() {
        int max = 0;
        for (List<DtwExample> list : allExamples) {
            for (DtwExample ex : list) {
                if (ex.getTimeSeries().size() > max) {
                    max = ex.getTimeSeries().size();
                }
            }            
        }
        maxSizeInExamples = max;
    }

    //Finds time series between minSize and MaxSize in most recent set of examples
    public List<TimeSeries> getCandidateSeriesFromCurrentRun(int minSize, int maxSize, int hopSize) {
        List<TimeSeries> l = new LinkedList<>();
        //hop size = 1

        if (currentTimeSeries.size() < minSize) {
            // l.add(new TimeSeries(t)); //don't do anything
            //  System.out.println("T too small: " + t.size());
            return l;
        }
        
        int shortestStartPos = currentTimeSeries.size() - minSize;
        int longestStartPos;
        if (currentTimeSeries.size() > maxSize) {
            longestStartPos = currentTimeSeries.size() - maxSize;
        } else {
            longestStartPos = 0;
        }

        //TODO: Can we make this more efficent?
        for (int startPos = longestStartPos; startPos <= shortestStartPos; startPos = startPos + hopSize) {
            TimeSeries tt = new TimeSeries(currentTimeSeries.numOfDimensions());
            for (int i = 0; i < currentTimeSeries.size() - startPos; i++) {
                double[] next = currentTimeSeries.getMeasurementVector(startPos + i);
                tt.addLast(new Double(i), new TimeSeriesPoint(next));
            }
            l.add(tt);
        }
        return l;
    }
    
    public void notifyExampleAddedListeners(int whichClass) {
        for (DtwDataListener l : dataListeners) {
            l.exampleAdded(whichClass);
        }
    }
    
    public void notifyExampleDeletedListeners(int whichClass) {
        for (DtwDataListener l : dataListeners) {
            l.exampleDeleted(whichClass);
        }
    }
    
    public void notifyExamplesChangedListeners(int whichClass, int currentNumExamples) {
        for (DtwDataListener l : dataListeners) {
            l.numExamplesChanged(whichClass, currentNumExamples);
        }
    }
    
    public void notifyAllExamplesDeletedListeners() {
        for (DtwDataListener l : dataListeners) {
            l.allExamplesDeleted();
        }
    }
    
    public void printState() {
        for (int i = 0; i < numGestures; i++) {
            System.out.println("CLASS " + i + ": + "
                    + allExamples.get(i).size()
                    + "points:");
            for (DtwExample ts : allExamples.get(i)) {                
                System.out.println(ts.getId() + ": " + ts.getTimeSeries());
            }
        }
        
        System.out.println("CURRENT Timeseries:");
        System.out.println(currentTimeSeries);
    }

    public void deleteExamplesForGesture(int gestureNum) {
        allExamples.get(gestureNum).clear();
        notifyExamplesChangedListeners(gestureNum, 0);
    }

    public void deleteMostRecentExample(int gestureNum) {
        DtwExample removed = allExamples.get(gestureNum).removeLast();
        if (removed != null) {
            notifyExampleDeletedListeners(gestureNum);
            notifyExamplesChangedListeners(gestureNum, allExamples.get(gestureNum).size());
        }
    }

    public String getSummaryString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getNumTotalExamples()).append(" examples in ");
        sb.append(numGestures).append(" gesture categories\n");
        sb.append("Max example length is ").append(getMaxSizeInExamples());
        sb.append("\nMin example length is ").append(getMinSizeInExamples()).append("\n");
        
        for (int i = 0; i < numGestures; i++) {
            sb.append("Examples for gesture ").append(i).append(":\n");
            sb.append(getSummaryStringForGesture(i));
        }
        sb.append("\n");
        return sb.toString();
    }

    public void deleteAllExamples() {
        /* for (DtwModel m : models) {
         m.deleteAllExamples();
         } */
        for (int i = 0; i < numGestures; i++) {
            deleteExamplesForGesture(numGestures);
        }
    }
    
    public interface DtwDataListener {

        public void exampleAdded(int whichClass);

        public void exampleDeleted(int whichClass);

        public void numExamplesChanged(int whichClass, int currentNumExamples);

        public void allExamplesDeleted();
    }
}