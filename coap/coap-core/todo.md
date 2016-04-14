TODO
====

- replace java logging with slf4j
- replace testng with junit (as it is used in other modules)
- make a benchmark test, that measures separate responses - which comes before empty ack [ONSP-1307]

- refactor transport interface
  ** remove executor from CoapServer
  ** add responsibility for thread management to transport implementation