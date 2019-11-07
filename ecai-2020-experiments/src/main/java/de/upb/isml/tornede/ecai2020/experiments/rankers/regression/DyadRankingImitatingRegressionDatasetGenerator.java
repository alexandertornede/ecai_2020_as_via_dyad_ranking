package de.upb.isml.tornede.ecai2020.experiments.rankers.regression;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import de.upb.isml.tornede.ecai2020.experiments.storage.DatasetFeatureRepresentationMap;
import de.upb.isml.tornede.ecai2020.experiments.storage.PipelineFeatureRepresentationMap;
import de.upb.isml.tornede.ecai2020.experiments.storage.PipelinePerformanceStorage;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;

public class DyadRankingImitatingRegressionDatasetGenerator extends AbstractRegressionDatasetGenerator {

	private Random random;

	private int lengthOfRanking;
	private int numberOfRankingsPerTrainingDataset;

	private ArrayList<Attribute> attributeInfo;

	public DyadRankingImitatingRegressionDatasetGenerator(PipelineFeatureRepresentationMap pipelineFeatureRepresentationMap, DatasetFeatureRepresentationMap datasetFeatureRepresentationMap,
			PipelinePerformanceStorage pipelinePerformanceStorage, int lengthOfRankings, int numberOfRankingsPerTrainingDataset) {
		super(pipelineFeatureRepresentationMap, datasetFeatureRepresentationMap, pipelinePerformanceStorage);
		this.lengthOfRanking = lengthOfRankings;
		this.numberOfRankingsPerTrainingDataset = numberOfRankingsPerTrainingDataset;
	}

	@Override
	public Instances generateTrainingDataset(List<Integer> trainingDatasetIds) {
		List<Integer> pipelineIds = pipelinePerformanceStorage.getPipelineIds();

		List<Attribute> datasetFeatureAttributes = createDatasetAttributeList();
		List<Attribute> pipelineFeatureAttributes = createPipelineAttributeList();
		Attribute targetAttribute = new Attribute("performance");
		attributeInfo = new ArrayList<>();
		attributeInfo.addAll(datasetFeatureAttributes);
		attributeInfo.addAll(pipelineFeatureAttributes);
		attributeInfo.add(targetAttribute);

		Instances instances = new Instances("dataset", attributeInfo, 0);
		instances.setClassIndex(instances.numAttributes() - 1);

		for (int pipelineId : pipelinePerformanceStorage.getPipelineIds()) {
			for (int trainingDatasetId : trainingDatasetIds) {
				double targetValue = pipelinePerformanceStorage.getPerformanceForPipelineWithIdOnDatasetWithId(pipelineId, trainingDatasetId);
				if (targetValue > 0) {
					Instance instance = createInstanceForPipelineAndDataset(pipelineId, trainingDatasetId);
					instance.setDataset(instances);
					instances.add(instance);
				}
			}
		}

		for (int datasetId : trainingDatasetIds) {
			for (int i = 0; i < numberOfRankingsPerTrainingDataset; i++) {
				List<Integer> pipelineIdsToUse = new ArrayList<>(lengthOfRanking);
				Set<Double> performancesSeen = new HashSet<>(lengthOfRanking);

				while (pipelineIdsToUse.size() < lengthOfRanking) {
					int randomPipelineId = pipelineIds.get(random.nextInt(pipelineIds.size()));
					if (!pipelineIdsToUse.contains(randomPipelineId)) {
						double performanceOfId = pipelinePerformanceStorage.getPerformanceForPipelineWithIdOnDatasetWithId(randomPipelineId, datasetId);
						if (performanceOfId > 0 && !performancesSeen.contains(performanceOfId)) {
							pipelineIdsToUse.add(randomPipelineId);
							performancesSeen.add(performanceOfId);

							Instance instance = createInstanceForPipelineAndDataset(randomPipelineId, datasetId);
							instance.setDataset(instances);
							instances.add(instance);
						}
					}
				}
			}
		}
		return instances;
	}

	@Override
	public void initialize(long randomSeed) {
		this.random = new Random(randomSeed);
	}

	@Override
	public String getName() {
		return "dyad_ranking_imitating_" + lengthOfRanking + "_" + numberOfRankingsPerTrainingDataset;
	}

	@Override
	public ArrayList<Attribute> getAttributeInfo() {
		return attributeInfo;
	}

}
