package org.agrfesta.btm.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class BtmApplication

fun main(args: Array<String>) {
    runApplication<BtmApplication>(*args)
}
