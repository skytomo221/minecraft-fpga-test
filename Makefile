# Makefile for Kotlin

.SUFFIXES: .kt .jar .run

.kt.jar:
    kotlinc $^ -include-runtime -d $@

.jar.run:
    java -jar $^

all:
