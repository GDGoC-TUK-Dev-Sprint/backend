package com.tukorea.gdgoc.koready.domain.expense.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ExpenseProperties.class)
class ExpenseConfig {
}
