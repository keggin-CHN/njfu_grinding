package com.examapp.util;

import java.util.List;

/**
 * A mock XGBoost predictor for score forecasting.
 * In a real-world scenario, this class would load a pre-trained XGBoost model
 * and use a proper Java XGBoost library to run predictions.
 */
public class XGBoostPredictor {

    /**
     * Predicts the next exam score based on a list of past scores.
     * This is a simplified mock implementation.
     *
     * @param scores A list of historical scores.
     * @return The predicted score.
     */
    public static double predict(List<Double> scores) {
        if (scores == null || scores.isEmpty()) {
            return 0.0; // No history, no prediction.
        }

        if (scores.size() == 1) {
            return scores.get(0); // If only one score, predict the same.
        }

        // Simple weighted average: give more weight to recent scores.
        double weightedSum = 0;
        double weightSum = 0;
        for (int i = 0; i < scores.size(); i++) {
            double score = scores.get(i);
            double weight = i + 1; // e.g., scores [80, 90] -> weights [1, 2]
            weightedSum += score * weight;
            weightSum += weight;
        }

        double prediction = weightedSum / weightSum;

        // Add a small random factor to simulate model variance
        prediction += (Math.random() - 0.5) * 5;

        // Ensure the prediction is within the valid range [0, 100]
        return Math.max(0, Math.min(100, prediction));
    }
}