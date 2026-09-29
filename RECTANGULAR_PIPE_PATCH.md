# Rectangular pipe support

The web converter accepts a pipe dimension field in either of these forms:

- `800`: circular pipe diameter in millimetres.
- `3800X5800`, `3800x5800`, or `3800×5800`: rectangular width and height in millimetres.

`ShapeConverter` converts rectangular dimensions to metres before passing them to Mago's rectangular modeler. It also compensates the existing vertical-offset path so that the generated section remains centred on the source line.

Regression data used by the companion web project contains 24 line features and a `DS` value of `3800X5800`. The expected generated vertical extent is 5.8 metres, not 5800 metres.

Build the executable JAR from the repository root with:

```shell
gradlew.bat :mago-tiler:shadowJar -x test
```
