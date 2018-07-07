# JMALib
MicroAvia Java libraries.

#### Modules List
 - jmalib-log: MicroAvia logs parsing (MicroAvia ULog v1 and v2)
 - jmalib-math: rotation and geo position conversion utils

Based on deprecated [jMAVlib](https://github.com/DrTon/jMAVlib) project with some major improvements. Added Maven support.

Log parser in a typical scenario has ~50x speed up compared to original jMAVlib parser.

#### Using with Maven
```
<dependency>
    <groupId>com.microavia</groupId>
    <artifactId>jmalib-log</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>

<dependency>
    <groupId>com.microavia</groupId>
    <artifactId>jmalib-math</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
        
```
