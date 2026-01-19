package com.embabel.dice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DiceServerApplication

fun main(args: Array<String>) {
    runApplication<DiceServerApplication>(*args)
}
