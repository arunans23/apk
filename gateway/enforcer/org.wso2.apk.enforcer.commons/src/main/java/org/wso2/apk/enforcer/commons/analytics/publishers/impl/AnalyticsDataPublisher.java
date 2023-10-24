/*
 *  Copyright (c) 2021, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.wso2.apk.enforcer.commons.analytics.publishers.impl;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.apk.enforcer.analytics.publisher.exception.MetricCreationException;
import org.wso2.apk.enforcer.analytics.publisher.reporter.CounterMetric;
import org.wso2.apk.enforcer.analytics.publisher.reporter.MetricReporter;
import org.wso2.apk.enforcer.analytics.publisher.reporter.MetricReporterFactory;
import org.wso2.apk.enforcer.analytics.publisher.reporter.MetricSchema;
import org.wso2.apk.enforcer.commons.analytics.AnalyticsCommonConfiguration;
import org.wso2.apk.enforcer.commons.analytics.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Analytics event publisher for APK.
 */
public class AnalyticsDataPublisher {

    private static final Log log = LogFactory.getLog(AnalyticsDataPublisher.class);
    private static final AnalyticsDataPublisher instance = new AnalyticsDataPublisher();

    private final List<CounterMetric> successMetricReporters = new ArrayList<>();
    private final List<CounterMetric> faultyMetricReporters = new ArrayList<>();

    private AnalyticsDataPublisher() {

    }

    public static AnalyticsDataPublisher getInstance() {

        return instance;
    }

    private CounterMetric getSuccessOrFaultyCounterMetrics(MetricReporter metricReporter, String name,
                                                           MetricSchema schema) {

        String reporterClassName = metricReporter.getClass().toString().replaceAll("[\r\n]", "");
        try {
            CounterMetric counterMetric = metricReporter.createCounterMetric(name, schema);
            if (counterMetric == null) {
                throw new MetricCreationException("AnalyticsDataPublisher is not initialized.");
            }
            return counterMetric;
        } catch (MetricCreationException | IllegalArgumentException e) {
            log.error("Error initializing event publisher for the Reporter of type " + reporterClassName, e);
        }
        return null;
    }

    public void initializeReporter(AnalyticsCommonConfiguration commonConfig) {

        Map<String, String> configs = commonConfig.getConfigurations();
        String reporterClass = configs.get("publisher.reporter.class");

        String reporterType = commonConfig.getType();
        try {
            MetricReporter metricReporter;
            if (reporterClass != null) {
                metricReporter = MetricReporterFactory.getInstance()
                        .createMetricReporter(reporterClass, configs);
            } else if (reporterType != null && !"".equals(reporterType)) {
                metricReporter = MetricReporterFactory.getInstance().createMetricReporterFromType(reporterType, configs);
            } else {
                metricReporter = MetricReporterFactory.getInstance().createMetricReporter(configs);
            }

            if (!StringUtils.isEmpty(commonConfig.getResponseSchema())) {

                this.successMetricReporters.add(
                        getSuccessOrFaultyCounterMetrics(metricReporter, Constants.RESPONSE_METRIC_NAME,
                                MetricSchema.valueOf(commonConfig.getResponseSchema())));
            } else {
                this.successMetricReporters.add(
                        getSuccessOrFaultyCounterMetrics(metricReporter, Constants.RESPONSE_METRIC_NAME,
                                MetricSchema.RESPONSE));
            }

            if (!StringUtils.isEmpty(commonConfig.getFaultSchema())) {
                this.faultyMetricReporters.add(
                        getSuccessOrFaultyCounterMetrics(metricReporter, Constants.FAULTY_METRIC_NAME,
                                MetricSchema.valueOf(commonConfig.getFaultSchema())));
            } else {
                this.faultyMetricReporters.add(
                        getSuccessOrFaultyCounterMetrics(metricReporter, Constants.FAULTY_METRIC_NAME,
                                MetricSchema.ERROR));
            }

            // not necessary to handle IllegalArgumentException here
            // since we are handling it in getSuccessOrFaultyCounterMetrics method
        } catch (MetricCreationException e) {
            log.error("Error while creating the metric reporter", e);
        }
    }

    public List<CounterMetric> getSuccessMetricReporters() throws MetricCreationException {

        if (this.successMetricReporters.isEmpty()) {
            throw new MetricCreationException("None of AnalyticsDataPublishers are initialized.");
        }
        return successMetricReporters;
    }

    public List<CounterMetric> getFaultyMetricReporters() throws MetricCreationException {

        if (this.faultyMetricReporters.isEmpty()) {
            throw new MetricCreationException("None of AnalyticsDataPublishers are initialized.");
        }
        return faultyMetricReporters;
    }
}
