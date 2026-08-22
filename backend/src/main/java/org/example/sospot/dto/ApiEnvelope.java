package org.example.sospot.dto;

import java.util.List;

public record ApiEnvelope<T>(String period, List<String> comparisonPeriods, T data) {}
