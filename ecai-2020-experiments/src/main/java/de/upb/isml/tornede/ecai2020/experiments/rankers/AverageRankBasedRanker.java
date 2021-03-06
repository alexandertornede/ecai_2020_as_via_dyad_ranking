package de.upb.isml.tornede.ecai2020.experiments.rankers;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import ai.libs.jaicore.basic.sets.Pair;
import de.upb.isml.tornede.ecai2020.experiments.rankers.regression.RegressionDatasetGenerator;
import de.upb.isml.tornede.ecai2020.experiments.storage.PipelinePerformanceStorage;

public class AverageRankBasedRanker implements IdBasedRanker {

	private PipelinePerformanceStorage pipelinePerformanceStorage;
	private RegressionDatasetGenerator regressionDatasetGenerator;
	private List<Integer> pipelineIds;
	private boolean useBayesianAveraging;

	// Stores the average performance as a sorted list (in descending order) of pipeline ids and their respective average performance across training datasets
	private List<Pair<Integer, Double>> averageRankOfPipelines;

	public AverageRankBasedRanker(PipelinePerformanceStorage pipelinePerformanceStorage, RegressionDatasetGenerator regressionDatasetGenerator, boolean useBayesianAveraging) {
		this.pipelinePerformanceStorage = pipelinePerformanceStorage;
		this.regressionDatasetGenerator = regressionDatasetGenerator;
		this.pipelineIds = pipelinePerformanceStorage.getPipelineIds();
		this.useBayesianAveraging = useBayesianAveraging;
	}

	@Override
	public void train(List<Integer> trainingDatasetIds, List<Integer> trainingPipelineIds) {
		if (useBayesianAveraging) {
			trainWithBayesianAveraging(trainingDatasetIds, trainingPipelineIds);
		} else {
			trainWithNormalAveraging(trainingDatasetIds, trainingPipelineIds);
		}
	}

	private void trainWithBayesianAveraging(List<Integer> trainingDatasetIds, List<Integer> trainingPipelineIds) {
		Map<Integer, DescriptiveStatistics> sparsePipelineToStatisticsMap = computeSparsePipelineToStatisticsMap(trainingDatasetIds, trainingPipelineIds);
		Map<Integer, DescriptiveStatistics> densePipelineToStatisticsMap = computeDensePipelineToStatisticsMap(trainingDatasetIds, trainingPipelineIds);

		for (Entry<Integer, DescriptiveStatistics> entry : sparsePipelineToStatisticsMap.entrySet()) {
			for (double performanceValue : entry.getValue().getValues()) {
				densePipelineToStatisticsMap.get(entry.getKey()).addValue(performanceValue);
			}
		}

		averageRankOfPipelines = pipelineIds.stream().map(id -> new Pair<>(id, -getAverageRankOfPipeline(id, densePipelineToStatisticsMap))).sorted(Comparator.comparingDouble(p -> ((Pair<Integer, Double>) p).getY()).reversed())
				.collect(Collectors.toList());
	}

	private Map<Integer, DescriptiveStatistics> computeDensePipelineToStatisticsMap(List<Integer> trainingDatasetIds, List<Integer> trainingPipelineIds) {
		Map<Integer, DescriptiveStatistics> pipelineToStatisticsMap = new HashMap<>();
		for (int datasetId : trainingDatasetIds) {
			List<Integer> pipelineSortedAccordingToPerformanceInDecreasingOrder = pipelineIds.stream().map(p -> new Pair<>(p, pipelinePerformanceStorage.getPerformanceForPipelineWithIdOnDatasetWithId(p, datasetId)))
					.sorted(Comparator.comparingDouble(p -> ((Pair<Integer, Double>) p).getY()).reversed()).map(p -> p.getX()).collect(Collectors.toList());
			for (int pipelineId : pipelineIds) {
				if (!pipelineToStatisticsMap.containsKey(pipelineId)) {
					pipelineToStatisticsMap.put(pipelineId, new DescriptiveStatistics());
				}
				double rankOfPipelineOnDataset = (pipelineSortedAccordingToPerformanceInDecreasingOrder.indexOf(pipelineId) + 1);
				pipelineToStatisticsMap.get(pipelineId).addValue(rankOfPipelineOnDataset);
			}
		}
		return pipelineToStatisticsMap;
	}

	private void trainWithNormalAveraging(List<Integer> trainingDatasetIds, List<Integer> trainingPipelineIds) {
		Map<Integer, DescriptiveStatistics> pipelineToStatisticsMap = computeSparsePipelineToStatisticsMap(trainingDatasetIds, trainingPipelineIds);
		averageRankOfPipelines = pipelineIds.stream().map(id -> new Pair<>(id, -getAverageRankOfPipeline(id, pipelineToStatisticsMap))).sorted(Comparator.comparingDouble(p -> ((Pair<Integer, Double>) p).getY()).reversed())
				.collect(Collectors.toList());
	}

	private Map<Integer, DescriptiveStatistics> computeSparsePipelineToStatisticsMap(List<Integer> trainingDatasetIds, List<Integer> trainingPipelineIds) {
		List<Pair<Integer, Integer>> datasetAndAlgorithmTrainingPairs = regressionDatasetGenerator.generateTrainingDataset(trainingDatasetIds, trainingPipelineIds).getY();

		Map<Integer, DescriptiveStatistics> pipelineToStatisticsMap = new HashMap<>();
		for (int datasetId : trainingDatasetIds) {
			List<Integer> pipelineIdsForDataset = datasetAndAlgorithmTrainingPairs.stream().filter(p -> p.getX().intValue() == datasetId).map(p -> p.getY()).collect(Collectors.toList());

			List<Integer> pipelineSortedAccordingToPerformanceInDecreasingOrder = pipelineIdsForDataset.stream().map(p -> new Pair<>(p, pipelinePerformanceStorage.getPerformanceForPipelineWithIdOnDatasetWithId(p, datasetId)))
					.sorted(Comparator.comparingDouble(p -> ((Pair<Integer, Double>) p).getY()).reversed()).map(p -> p.getX()).collect(Collectors.toList());
			for (int pipelineId : pipelineIdsForDataset) {
				if (!pipelineToStatisticsMap.containsKey(pipelineId)) {
					pipelineToStatisticsMap.put(pipelineId, new DescriptiveStatistics());
				}
				double rankOfPipelineOnDataset = (pipelineSortedAccordingToPerformanceInDecreasingOrder.indexOf(pipelineId) + 1);
				pipelineToStatisticsMap.get(pipelineId).addValue(rankOfPipelineOnDataset);
			}
		}
		return pipelineToStatisticsMap;
	}

	private double getAverageRankOfPipeline(int pipelineId, Map<Integer, DescriptiveStatistics> pipelineToStatisticsMap) {
		if (!pipelineToStatisticsMap.containsKey(pipelineId)) {
			return pipelineIds.size();
		}
		return pipelineToStatisticsMap.get(pipelineId).getMean();
	}

	@Override
	public List<Pair<Integer, Double>> getRankingOfPipelinesOnDataset(List<Integer> pipelineIdsToRank, int datasetId) {
		// System.out.println("average rank: " + averageRankOfPipelines.stream().filter(p -> pipelineIdsToRank.contains(p.getX())).collect(Collectors.toList()));
		return averageRankOfPipelines.stream().filter(p -> pipelineIdsToRank.contains(p.getX())).collect(Collectors.toList());
	}

	@Override
	public String getName() {
		return "average_rank_" + regressionDatasetGenerator.getName() + (useBayesianAveraging ? "_bayesianAveraging" : "");
	}

	@Override
	public void initialize(long randomSeed) {
		regressionDatasetGenerator.initialize(randomSeed);
	}
}
