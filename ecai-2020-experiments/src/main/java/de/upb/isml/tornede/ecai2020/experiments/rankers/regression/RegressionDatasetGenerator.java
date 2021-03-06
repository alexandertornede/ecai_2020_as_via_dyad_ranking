package de.upb.isml.tornede.ecai2020.experiments.rankers.regression;

import java.util.ArrayList;
import java.util.List;

import ai.libs.jaicore.basic.sets.Pair;
import weka.core.Attribute;
import weka.core.Instances;

public interface RegressionDatasetGenerator {

	public Pair<Instances, List<Pair<Integer, Integer>>> generateTrainingDataset(List<Integer> trainingDatasetIds, List<Integer> trainingPipelineIds);

	public void initialize(long randomSeed);

	public String getName();

	public ArrayList<Attribute> getAttributeInfo();

}
