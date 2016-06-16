#include <stdio.h>  
#include <stdlib.h>  
#include <errno.h>  
#include <string.h>  
#include <sys/types.h>  
#include <netinet/in.h>  
#include <sys/socket.h>  
#include <sys/wait.h>  
#include <arpa/inet.h>  
#include <unistd.h>
#include <iostream>
#include <fstream>
#include <sstream>
#include <string>
#include <map>
#include <google/protobuf/io/zero_copy_stream_impl.h>
#include <google/protobuf/io/zero_copy_stream_impl_lite.h>
#include <google/protobuf/io/coded_stream.h>
#include <google/protobuf/io/zero_copy_stream.h>

#include "SensorService.pb.h"

#define NETWORK_CONFIG 					"./network.conf"
#define SERVER_IP_KEY					"server"
#define SERVER_RECEIVE_PORT_KEY 		"receive"
#define SERVER_SEND_POR_KEY				"send"

using namespace google::protobuf::io;
using namespace std;

int sendFd, receiveFd;
map<string, string> configs;

int parseNetworkConf();
int initNetwork();

int main()
{
	char* buff;

	if (parseNetworkConf() == -1)
	{
		cout << "parse network config file error." << endl;
		exit(1);
	}

	if (initNetwork() == -1)
	{
		cout << "init network error." << endl;
		exit(1);
	}
	
	/*
	 * TEST CODE, PLEASE REMOVE IT WHEN RELEASE. 
	 */
	bool clean_eof;
	ZeroCopyInputStream* input = new FileInputStream(receiveFd);
	/*
	// here we get all the sensor list.
	SensorService::Request sensorlist_request;
	SensorService::Response sensorlist_response;
	sensorlist_request.set_reqtype(SensorService::Request::GET_SENSOR_LIST);
	sensorlist_request.SerializeDelimitedToFileDescriptor(sendFd);

	sensorlist_response.ParseDelimitedFromZeroCopyStream(input, &clean_eof);
	printf("sensor size: %d \n", sensorlist_response.sensors_size());
	for (int i = 0; i < sensorlist_response.sensors_size(); i++)
	{
		cout << sensorlist_response.sensors(i).name() << endl;
	}
	*/


	// here we get one sensor data.
	SensorService::Request sensordata_request_one;
	SensorService::Request sensordata_request_two;
	SensorService::Response sensordata_response;

	sensordata_request_one.set_reqtype(SensorService::Request::REGISTER);
	SensorService::RegisterListener listener_one;
	listener_one.set_type(SensorService::RegisterListener::STREAM);
	listener_one.set_sensorhandle(2);
	sensordata_request_one.set_allocated_reglistener(&listener_one);
	sensordata_request_one.SerializeDelimitedToFileDescriptor(sendFd);

	/*
	sensordata_request_two.set_reqtype(SensorService::Request::REGISTER);
	SensorService::RegisterListener listener_two;
	listener_two.set_type(SensorService::RegisterListener::STREAM);
	listener_two.set_sensorhandle(1);
	sensordata_request_two.set_allocated_reglistener(&listener_two);
	sensordata_request_two.SerializeDelimitedToFileDescriptor(sendFd);
	*/


	printf("read from socket. \n");
	SensorService::SensorDataEvent event;
	while (true) {
		sensordata_response.ParseDelimitedFromZeroCopyStream(input, &clean_eof);
		event = sensordata_response.sensordata();
		printf("clean_eof: %d \n", clean_eof);
		printf("handle: %d \n", event.sensorhandle());
		printf("data x: %f \n", event.xvalue());
		printf("data y: %f \n", event.yvalue());
		printf("data z: %f \n", event.zvalue());
		sensordata_response.Clear();
	}

	return 0;
}

int parseNetworkConf() {
	ifstream confFile;
	string line;
	int count = 3;

	confFile.open(NETWORK_CONFIG, ios::in);

	if (!confFile)
	{
		perror("open config file error!");
		return -1;
	}

	while (count--)
	{
		getline(confFile, line);
		string::size_type index = line.find_first_of(" ", 0);
		string name = line.substr(0, index);
		string value = line.substr(index + 1);
		configs[name] = value;
	}

	return 0;
}

int initNetwork()
{
	const char* value;
	stringstream ss;
	// server ip and ports
	const char* ip;
	unsigned short receive_port;
	unsigned short send_port;
	// network struct
	struct sockaddr_in serv_rcv_addr;
	struct sockaddr_in serv_snd_addr;

	ip = configs[SERVER_IP_KEY].c_str();

	value = configs[SERVER_RECEIVE_PORT_KEY].c_str();
	receive_port = atoi(value);

	value = configs[SERVER_SEND_POR_KEY].c_str();
	send_port = atoi(value);

	if ((receiveFd = socket(AF_INET, SOCK_STREAM, 0)) == -1)
	{
		perror("socket error!");
		return -1;
	}

	if ((sendFd = socket(AF_INET, SOCK_STREAM, 0)) == -1)
	{
		perror("socket error!");
		return -1;
	}

	// receive address
	bzero(&serv_rcv_addr, sizeof(sockaddr_in));
	serv_rcv_addr.sin_family = AF_INET;
	serv_rcv_addr.sin_port = htons(receive_port);
	serv_rcv_addr.sin_addr.s_addr = inet_addr(ip);

	// send address
	bzero(&serv_snd_addr, sizeof(sockaddr_in));
	serv_snd_addr.sin_family = AF_INET;
	serv_snd_addr.sin_port = htons(send_port);
	serv_snd_addr.sin_addr.s_addr = inet_addr(ip);

	// connect to receive socket first.
	if (connect(sendFd, (struct sockaddr *)&serv_rcv_addr, sizeof(struct sockaddr)) == -1)
	{
		perror("connect receive error!");
		return -1;
	}
	
	// connect to send socket here.
	if (connect(receiveFd, (struct sockaddr *)&serv_snd_addr, sizeof(struct sockaddr)) == -1)
	{
		perror("connect send error!");
		return -1;
	}

	return 0;
}
