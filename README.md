**Programmable Payment Channels**

This repository is based on the paper Programmable Payment Channels. One approach for scaling blockchains is the creation of bilateral, offchain channels, known as payment/state channels, which protect parties against cheating via onchain collateralization. While extensive studies have been conducted on these channels, limited attention has been given to their programmability. Programmability allows parties to dynamically enforce arbitrary conditions over their payments without going onchain.

We introduce the concept of a Programmable Payment Channel (PPC) that enables two parties to set dynamic terms and conditions for each payment using a smart contract. The verification of payment conditions and the payment itself occurs offchain, provided the parties behave honestly. If any terms are violated, the smart contract can be deployed onchain to enforce the agreed remedy. This repository contains implementations and contributions as discussed in the paper.

This repository contains implementations and contributions as discussed in the paper: https://eprint.iacr.org/2023/347




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
