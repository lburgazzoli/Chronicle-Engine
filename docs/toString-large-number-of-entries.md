# toString

for a large number of entries, just the first 100 records are returned and displayed in the
toString()

```
 ChronicleMap<Integer, String> map = createMap();
 map.put(1, "hello");
 map.put(2, "world");
 System.out.println(map.toString());
```

--------------------------------------------
client writes:

type: MAP
transactionId: 1426515176235
timeStamp: 1426515176235
channelId: 1
methodName: put
arg1: test
arg2: { keyClass: java.lang.Integer
valueClass: java.lang.String
channelID: 2
}
--------------------------------------------
server reads:

type: MAP
transactionId: 1426515176235
timeStamp: 1426515176235
channelId: 1
methodName: put
arg1: test
arg2: { keyClass: java.lang.Integer
valueClass: java.lang.String
channelID: 2
}
--------------------------------------------
server writes:

transactionId: 1426515176235
isException: false
resultIsNull: true


--------------------------------
client read:

transactionId: 1426515176235
isException: false
resultIsNull: true


--------------------------------------------
client writes:


type: MAP
transactionId: 1426515176242
timeStamp: 1426515176242
channelId: 2
methodName: put
arg1: 1
arg2: hello

--------------------------------------------
server reads:


type: MAP
transactionId: 1426515176242
timeStamp: 1426515176242
channelId: 2
methodName: put
arg1: 1
arg2: hello

--------------------------------------------
server writes:

transactionId: 1426515176242
isException: false
resultIsNull: true


--------------------------------
client read:

transactionId: 1426515176242
isException: false
resultIsNull: true


--------------------------------------------
client writes:

type: MAP
transactionId: 1426515176244
timeStamp: 1426515176244
channelId: 2
methodName: put
arg1: 2
arg2: world

--------------------------------------------
server reads:

type: MAP
transactionId: 1426515176244
timeStamp: 1426515176244
channelId: 2
methodName: put
arg1: 2
arg2: world

--------------------------------------------
server writes:

transactionId: 1426515176244
isException: false
resultIsNull: true


--------------------------------
client read:

transactionId: 1426515176244
isException: false
resultIsNull: true


--------------------------------------------
client writes:

type: MAP
transactionId: 1426515176246
timeStamp: 1426515176246
channelId: 2
methodName: longSize

--------------------------------------------
server reads:

type: MAP
transactionId: 1426515176246
timeStamp: 1426515176246
channelId: 2
methodName: longSize

--------------------------------------------
server writes:

transactionId: 1426515176246
isException: false
result: 2


--------------------------------
client read:

transactionId: 1426515176246
isException: false
result: 2


--------------------------------------------
client writes:

type: MAP
transactionId: 1426515176250
timeStamp: 1426515176250
channelId: 2
methodName: entrySet

--------------------------------------------
server reads:

type: MAP
transactionId: 1426515176250
timeStamp: 1426515176250
channelId: 2
methodName: entrySet

--------------------------------------------
server writes:

transactionId: 1426515176250
isException: false
hasNext: true
resultKey: 1
resultValue: hello
isException: false
hasNext: true
resultKey: 2
resultValue: world
isException: false
hasNext: false


--------------------------------
client read:

transactionId: 1426515176250
isException: false
hasNext: true
resultKey: 1
resultValue: hello
isException: false
hasNext: true
resultKey: 2
resultValue: world
isException: false
hasNext: false

{1=hello, 2=world}