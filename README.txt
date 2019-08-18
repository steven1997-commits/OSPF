This program was tested using GNU Make 4.1

The goal of this program is to compute shortest paths from each router
to every other router in a network using the OSPF (Djikstra's) algorithm.

nse-linux386 acts as an emulator and will send packets to each router
containing information on paths and their costs. The routers in turn,
will send their packets to the emulator instead of other routers directly.

It is assumed that there will be enough time in between packets to 
receive and process them all without having to implement multi-threading.

Instructions:
 - Make sure all files including Makefile are in the same directory folder
 - Enter in the command "make"
 - After the host was started (./nse-linux386 <host address> <host port>),
   start each server individually by typing in "./router.sh <router id> <address of host> <host port> <router port>"
 - There should be a total of 5 routers numbered 1 to 5
 - The routers should be started in order
 - Open the router's log file to see the output of the RIB,
   which will begin with the line "Logging RIB..." 