/*
 * ARX: Powerful Data Anonymization
 * Copyright 2012 - 2016 Fabian Prasser, Florian Kohlmayer and contributors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.deidentifier.arx.risk;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.deidentifier.arx.DataHandleInternal;
import org.deidentifier.arx.common.WrappedBoolean;
import org.deidentifier.arx.common.WrappedInteger;
import org.deidentifier.arx.exceptions.ComputationInterruptedException;

import de.linearbits.suda2.SUDA2;
import de.linearbits.suda2.SUDA2ListenerProgress;
import de.linearbits.suda2.SUDA2StatisticsScores;

/**
 * A risk model based on MSUs in the data set, returning score statistics
 * @author Fabian Prasser
 */
public class RiskModelMSUScoreStatistics {

    /** Progress stuff */
    private final WrappedInteger progress;
    /** Progress stuff */
    private final WrappedBoolean stop;
    /** Maximal size of keys considered */
    private final int            maxKeyLength;
    /** Contributions of each column */
    private final double[]       scoresSUDA;
    /** Distribution of sizes of keys */
    private final double[]       scoresDIS;
    /** Attributes */
    private final String[]       attributes;

    /**
     * Creates a new instance
     * @param handle
     * @param identifiers
     * @param stop 
     * @param progress 
     * @param maxKeyLength
     * @param samplingFraction
     * @param sdcMicroScore
     */
    RiskModelMSUScoreStatistics(DataHandleInternal handle, 
                 Set<String> identifiers, 
                 WrappedInteger progress, 
                 WrappedBoolean stop,
                 int maxKeyLength,
                 double samplingFraction,
                 boolean sdcMicroScore) {

        // Store
        this.stop = stop;
        this.progress = progress;
        maxKeyLength = maxKeyLength < 0 ? 0 : maxKeyLength;
        
        // Add all attributes, if none were specified
        if (identifiers == null || identifiers.isEmpty()) {
            identifiers = new HashSet<String>();
            for (int column = 0; column < handle.getNumColumns(); column++) {
                identifiers.add(handle.getAttributeName(column));
            }
        }
        
        // Build column array
        int[] columns = getColumns(handle, identifiers);
        attributes = getAttributes(handle, columns);
        
        // Update progress
        progress.value = 10;
        checkInterrupt();
        
        // Do something
        SUDA2 suda2 = new SUDA2(handle.getDataArray(columns).getArray());
        suda2.setProgressListener(new SUDA2ListenerProgress() {
            @Override
            public void update(double progress) {
                RiskModelMSUScoreStatistics.this.progress.value = 10 + (int)(progress * 90d);
            }
            @Override
            public void tick() {
                checkInterrupt();
            }
            
        });
        SUDA2StatisticsScores result = suda2.getStatisticsScores(maxKeyLength, sdcMicroScore);
        this.maxKeyLength = result.getMaxKeyLengthConsidered();
        this.scoresSUDA = result.getSUDAScores();
        this.scoresDIS = result.getDISScores(samplingFraction);
    }
    
    /**
     * Returns the attributes addressed by the statistics
     * @return
     */
    public String[] getAttributes() {
        return attributes;
    }

    /**
     * @return the scoresSUDA
     */
    public double[] getScoresSUDA() {
        return scoresSUDA;
    }

    /**
     * @return the scoresDIS
     */
    public double[] getScoresDIS() {
        return scoresDIS;
    }

    /**
     * Returns the maximal length of keys searched for
     * @return
     */
    public int getMaxKeyLengthConsidered() {
        return maxKeyLength;
    }
    
    /**
     * Checks for interrupts
     */
    private void checkInterrupt() {
        if (stop.value) { throw new ComputationInterruptedException(); }
    }

    /**
     * Returns the column array
     * @param handle
     * @param columns
     * @return
     */
    private String[] getAttributes(DataHandleInternal handle, int[] columns) {
        String[] result = new String[columns.length];
        int index = 0;
        for (int column : columns) {
            result[index++] = handle.getAttributeName(column);
        }
        return result;
    }

    /**
     * Returns the column array
     * @param handle
     * @param identifiers
     * @return The columns in ascending order
     */
    private int[] getColumns(DataHandleInternal handle, Set<String> identifiers) {
        int[] result = new int[identifiers.size()];
        int index = 0;
        for (String attribute : identifiers) {
            int column = handle.getColumnIndexOf(attribute);
            if (column == -1) {
                throw new IllegalArgumentException("Unknown attribute '" + attribute+"'");
            }
            result[index++] = column;
        }
        Arrays.sort(result);
        return result;
    }
}
