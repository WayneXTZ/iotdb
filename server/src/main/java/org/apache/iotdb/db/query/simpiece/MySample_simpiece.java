/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

// Sim-Piece code forked from https://github.com/xkitsios/Sim-Piece.git

package org.apache.iotdb.db.query.simpiece;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Comparator;
import java.util.List;

public class MySample_simpiece {

  public static void main(String[] args) {
    String fileDir = "D:\\desktop\\NISTPV\\";
    String[] datasetNameList =
        new String[] {
          "NISTPV-Ground-2015-Qloss_Ah",
          "NISTPV-Ground-2015-Pyra1_Wm2",
          "NISTPV-Ground-2015-RTD_C_3"
        };
    int[] noutList = new int[] {100};
    double[] r = new double[] {0.1, 0.5, 1.3};
    double[] epsilonList = new double[] {0.0009999, 316.5642651891633, 9.186667042922977};
    for (int y = 0; y < datasetNameList.length; y++) {
      String datasetName = datasetNameList[y];
      int start = (int) (10000000 / 2 - 2500000 * r[y]); // 从0开始计数
      int end = (int) (10000000 / 2 + 2500000 * (1 - r[y]));
      int N = end - start;
      //      int start = 0;
      //      int end = 10000;
      //      int N = end - start;

      for (int nout : noutList) {
        // apply Sim-Piece on the input file, outputting nout points saved in csvFile
        boolean hasHeader = false;
        try (FileInputStream inputStream = new FileInputStream(fileDir + datasetName + ".csv")) {
          String delimiter = ",";
          TimeSeries ts =
              TimeSeriesReader.getMyTimeSeries(
                  inputStream, delimiter, false, N, start, hasHeader, false);
          //          double epsilon = getSimPieceParam(nout, ts, 1e-6);
          double epsilon = epsilonList[y];
          SimPiece simPiece = new SimPiece(ts.data, epsilon);
          System.out.println(
              datasetName
                  + ": n="
                  + N
                  + ",m="
                  + nout
                  + ",epsilon="
                  + epsilon
                  + ",actual m="
                  + simPiece.segments.size() * 2);
          List<SimPieceSegment> segments = simPiece.segments;
          segments.sort(Comparator.comparingLong(SimPieceSegment::getInitTimestamp));
          try (PrintWriter writer =
              new PrintWriter(
                  new FileWriter(
                      datasetName + "-" + N + "-" + segments.size() * 2 + "-simpiece.csv"))) {
            for (int i = 0; i < segments.size() - 1; i++) {
              // start point of this segment
              writer.println(segments.get(i).getInitTimestamp() + "," + segments.get(i).getB());
              // end point of this segment
              double v =
                  (segments.get(i + 1).getInitTimestamp() - segments.get(i).getInitTimestamp())
                          * segments.get(i).getA()
                      + segments.get(i).getB();
              writer.println(segments.get(i + 1).getInitTimestamp() + "," + v);
            }
            // the two end points of the last segment
            writer.println(
                segments.get(segments.size() - 1).getInitTimestamp()
                    + ","
                    + segments.get(segments.size() - 1).getB());
            double v =
                (simPiece.lastTimeStamp - segments.get(segments.size() - 1).getInitTimestamp())
                        * segments.get(segments.size() - 1).getA()
                    + segments.get(segments.size() - 1).getB();
            writer.println(simPiece.lastTimeStamp + "," + v);
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
  }

  public static double getSimPieceParam(int nout, TimeSeries ts, double accuracy)
      throws IOException {
    double epsilon = ts.range * 0.001;
    while (true) {
      SimPiece simPiece = new SimPiece(ts.data, epsilon);
      if (simPiece.segments.size() * 2 > nout) { // note *2 for disjoint
        epsilon *= 2;
      } else {
        break;
      }
    }
    double left = epsilon / 2;
    double right = epsilon;
    while (Math.abs(right - left) > accuracy) {
      double mid = (left + right) / 2;
      SimPiece simPiece = new SimPiece(ts.data, mid);
      if (simPiece.segments.size() * 2 > nout) { // note *2 for disjoint
        left = mid;
      } else {
        right = mid;
      }
    }
    return (left + right) / 2;
  }
}
