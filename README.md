**1) Install OpenZeppelin contracts**

```
cd sdk/src/main/contracts
npm install
```

**2) Compile smart contracts and generate wrappers**

Find the desired solidity version and os here:

https://raw.githubusercontent.com/web3j/web3j-sokt/master/src/main/resources/releases.json

Download it to \~/.web3j/solc/\<version\>/solc and mark it as executable. Now you should be able to run the maven task web3j:generate-sources

```
cd sdk
mvn web3j:generate-sources
```

**3) Running tests**

In order to run the UPCConsistencyTest and the e2e tests, the easiest method is to run a local ethereum network with gas price set to 0. 
See the run.sh script in the docker/ directory for an example. For tests, modify sdk/src/test/resources/test.properties to point to the rpc endpoint
of your ethereum network.

**4) Running the example**

First, install the UPC library jar to your local maven repository.

```
cd sdk
mvn clean install
cd ../example
mvn clean compile
```

Then, see (3) to deploy a local ethereum network with free gas. Now you should be able to run Main.
