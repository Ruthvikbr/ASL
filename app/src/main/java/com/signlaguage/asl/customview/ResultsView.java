package com.signlaguage.asl.customview;

import com.signlaguage.asl.tflite.Classifier;

import java.util.List;


public interface ResultsView {
    public void setResults(final List<Classifier.Recognition> results);
}
