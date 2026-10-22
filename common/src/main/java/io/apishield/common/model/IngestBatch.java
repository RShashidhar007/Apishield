package io.apishield.common.model;

import java.util.List;

public record IngestBatch(String gatewayInstance, List<TrafficEvent> events) {}
