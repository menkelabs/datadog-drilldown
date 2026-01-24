package com.example.testreport

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TestReportServerApplication

fun main(args: Array<String>) {
    runApplication<TestReportServerApplication>(*args)
}
