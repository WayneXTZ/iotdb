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

package org.apache.iotdb.confignode.manager.load.balancer.router.leader;

import org.apache.iotdb.common.rpc.thrift.TConsensusGroupId;
import org.apache.iotdb.common.rpc.thrift.TDataNodeLocation;
import org.apache.iotdb.common.rpc.thrift.TRegionReplicaSet;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/** Leader distribution balancer that uses greedy algorithm */
public class GreedyLeaderBalancer implements ILeaderBalancer {

  private final Map<TConsensusGroupId, TRegionReplicaSet> regionReplicaSetMap;
  private final Map<TConsensusGroupId, Integer> regionLeaderMap;
  private final Set<Integer> disabledDataNodeSet;

  public GreedyLeaderBalancer() {
    this.regionReplicaSetMap = new HashMap<>();
    this.regionLeaderMap = new ConcurrentHashMap<>();
    this.disabledDataNodeSet = new HashSet<>();
  }

  @Override
  public Map<TConsensusGroupId, Integer> generateOptimalLeaderDistribution(
      Map<String, List<TConsensusGroupId>> databaseRegionGroupMap,
      Map<TConsensusGroupId, TRegionReplicaSet> regionReplicaSetMap,
      Map<TConsensusGroupId, Integer> regionLeaderMap,
      Set<Integer> disabledDataNodeSet) {
    initialize(regionReplicaSetMap, regionLeaderMap, disabledDataNodeSet);

    Map<TConsensusGroupId, Integer> result = constructGreedyDistribution();

    clear();
    return result;
  }

  private void initialize(
      Map<TConsensusGroupId, TRegionReplicaSet> regionReplicaSetMap,
      Map<TConsensusGroupId, Integer> regionLeaderMap,
      Set<Integer> disabledDataNodeSet) {
    this.regionReplicaSetMap.putAll(regionReplicaSetMap);
    this.regionLeaderMap.putAll(regionLeaderMap);
    this.disabledDataNodeSet.addAll(disabledDataNodeSet);
  }

  private void clear() {
    this.regionReplicaSetMap.clear();
    this.regionLeaderMap.clear();
    this.disabledDataNodeSet.clear();
  }

  private Map<TConsensusGroupId, Integer> constructGreedyDistribution() {
    Map<Integer, Integer> leaderCounter = new TreeMap<>();
    regionReplicaSetMap.forEach(
        (regionGroupId, regionGroup) -> {
          int minCount = Integer.MAX_VALUE,
              leaderId = regionLeaderMap.getOrDefault(regionGroupId, -1);
          for (TDataNodeLocation dataNodeLocation : regionGroup.getDataNodeLocations()) {
            int dataNodeId = dataNodeLocation.getDataNodeId();
            if (disabledDataNodeSet.contains(dataNodeId)) {
              continue;
            }
            // Select the DataNode with the minimal leader count as the new leader
            int count = leaderCounter.getOrDefault(dataNodeId, 0);
            if (count < minCount) {
              minCount = count;
              leaderId = dataNodeId;
            }
          }
          regionLeaderMap.put(regionGroupId, leaderId);
          leaderCounter.merge(leaderId, 1, Integer::sum);
        });
    return new ConcurrentHashMap<>(regionLeaderMap);

    //    /* Count the number of leaders that each DataNode have */
    //    // Map<DataNodeId, leader count>
    //    Map<Integer, Integer> leaderCounter = new ConcurrentHashMap<>();
    //    regionLeaderMap.forEach(
    //        (regionGroupId, leaderId) -> leaderCounter.merge(leaderId, 1, Integer::sum));
    //
    //    /* Ensure all RegionGroups' leader are not inside disabled DataNodes */
    //    for (TConsensusGroupId regionGroupId : regionReplicaSetMap.keySet()) {
    //      int leaderId = regionLeaderMap.get(regionGroupId);
    //      if (disabledDataNodeSet.contains(leaderId)) {
    //        int newLeaderId = -1;
    //        int newLeaderWeight = Integer.MAX_VALUE;
    //        for (TDataNodeLocation candidate :
    //            regionReplicaSetMap.get(regionGroupId).getDataNodeLocations()) {
    //          int candidateId = candidate.getDataNodeId();
    //          int candidateWeight = leaderCounter.get(candidateId);
    //          // Select the available DataNode with the fewest leaders
    //          if (!disabledDataNodeSet.contains(candidateId) && candidateWeight < newLeaderWeight)
    // {
    //            newLeaderId = candidateId;
    //            newLeaderWeight = candidateWeight;
    //          }
    //        }
    //
    //        if (newLeaderId != -1) {
    //          leaderCounter.merge(leaderId, -1, Integer::sum);
    //          leaderCounter.merge(newLeaderId, 1, Integer::sum);
    //          regionLeaderMap.replace(regionGroupId, newLeaderId);
    //        }
    //      }
    //    }
    //
    //    /* Double keyword sort */
    //    List<WeightEntry> weightList = new ArrayList<>();
    //    for (TConsensusGroupId regionGroupId : regionReplicaSetMap.keySet()) {
    //      int leaderId = regionLeaderMap.get(regionGroupId);
    //      int leaderWeight = leaderCounter.get(regionLeaderMap.get(regionGroupId));
    //
    //      int followerWeight = Integer.MAX_VALUE;
    //      for (TDataNodeLocation follower :
    //          regionReplicaSetMap.get(regionGroupId).getDataNodeLocations()) {
    //        int followerId = follower.getDataNodeId();
    //        if (followerId != leaderId) {
    //          followerWeight = Math.min(followerWeight, leaderCounter.get(followerId));
    //        }
    //      }
    //
    //      weightList.add(new WeightEntry(regionGroupId, leaderWeight, followerWeight));
    //    }
    //    weightList.sort(WeightEntry.COMPARATOR);
    //
    //    /* Greedy distribution */
    //    for (WeightEntry weightEntry : weightList) {
    //      TConsensusGroupId regionGroupId = weightEntry.regionGroupId;
    //      int leaderId = regionLeaderMap.get(regionGroupId);
    //      int leaderWeight = leaderCounter.get(regionLeaderMap.get(regionGroupId));
    //
    //      int newLeaderId = -1;
    //      int newLeaderWeight = Integer.MAX_VALUE;
    //      for (TDataNodeLocation candidate :
    //          regionReplicaSetMap.get(regionGroupId).getDataNodeLocations()) {
    //        int candidateId = candidate.getDataNodeId();
    //        int candidateWeight = leaderCounter.get(candidateId);
    //        if (!disabledDataNodeSet.contains(candidateId)
    //            && candidateId != leaderId
    //            && candidateWeight < newLeaderWeight) {
    //          newLeaderId = candidateId;
    //          newLeaderWeight = candidateWeight;
    //        }
    //      }
    //
    //      // Redistribution takes effect only when leaderWeight - newLeaderWeight > 1.
    //      // i.e. Redistribution can reduce the range of the number of leaders that each DataNode
    // owns.
    //      if (leaderWeight - newLeaderWeight > 1) {
    //        leaderCounter.merge(leaderId, -1, Integer::sum);
    //        leaderCounter.merge(newLeaderId, 1, Integer::sum);
    //        regionLeaderMap.replace(regionGroupId, newLeaderId);
    //      }
    //    }
  }

  private static class WeightEntry {

    private final TConsensusGroupId regionGroupId;
    // The number of leaders owned by DataNode where the RegionGroup's leader resides
    private final int firstKey;
    // The minimum number of leaders owned by DataNode where the  RegionGroup's followers reside
    private final int secondKey;

    private WeightEntry(TConsensusGroupId regionGroupId, int firstKey, int secondKey) {
      this.regionGroupId = regionGroupId;
      this.firstKey = firstKey;
      this.secondKey = secondKey;
    }

    // Compare the first key by descending order and the second key by ascending order.
    private static final Comparator<WeightEntry> COMPARATOR =
        (o1, o2) ->
            o1.firstKey == o2.firstKey ? o1.secondKey - o2.secondKey : o2.firstKey - o1.firstKey;
  }
}
