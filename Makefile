default:
	javac *.java

run: default
	java Sender

clean:
	rm *.class