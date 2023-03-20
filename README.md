# JVM/Clojure AWS Lambda Sideloader

A library to sideload code into a live JVM/Clojure AWS Lambda.

The idea is to first build a JVM/Clojure AWS Lambda in the usual way, by AOT compiling Clojure code into Java bytecode, but in the handler, call a sideloader, that puts new Clojure code into the Classpath, by downloading a zip from S3.

If there is new Clojure code available, the sideloader calls `(require '<ns-sym> :reload-all)` to reload application namespaces. The idea is to exclude Clojure source code from the AOT bundle, so when reloading with sideloaded Clojure code on the classpath, we let the Clojure compiler [pick up the new source code](https://github.com/clojure/clojure/blob/master/src/jvm/clojure/lang/RT.java#L440-L460), if it is newer than the compiled class files

There is an example in the [example](example/) folder, that uses [pod-babashka-fswatcher](https://github.com/babashka/pod-babashka-fswatcher) to package application Clojure code into a zip file and upload to S3, when there are changes in the source folder.

This way, you can write new code, and have it loaded just before handling an event in the Lambda.

https://user-images.githubusercontent.com/57011/226206498-606be685-0760-4186-a551-4e7802ffa1f1.mov

