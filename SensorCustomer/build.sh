#########################################################################
# File Name: build.sh
# Author: Baniel Gao
# mail: createchance@163.com
# Created Time: Wed 11 May 2016 11:28:13 AM CST
#########################################################################
#!/bin/bash

g++ SensorService.pb.cc test.cpp -o test `pkg-config --cflags --libs protobuf`
