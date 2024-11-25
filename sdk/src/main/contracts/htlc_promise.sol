pragma solidity ^0.8.6;
pragma experimental ABIEncoderV2;

contract HTLC{
    uint256 amount;
	bytes32 hash;
	uint256 expiry;
    bytes32 key;
    bool keyWasRevealed;

	constructor (uint256 _amount, bytes32 _hash, uint256 _expiry) {
		amount = _amount;
		hash = _hash;
		expiry = _expiry;
    }
    function revealKey(bytes32 _key) public {
		require(block.timestamp <= expiry, "htlc expiry has passed");
		require(sha256(abi.encode(_key)) == hash, "key does not Hash to the hash value");
		key = _key;
		keyWasRevealed = true;
    }

	function testKey(bytes32 _key, bytes32 _hash) public pure returns (bool){
		return sha256(abi.encode(_key)) == _hash;
	}

    function resolve() public view returns (uint256) {
		if (keyWasRevealed) {
			return amount;
		} else {
			require (block.timestamp > expiry);
			return 0;
		}
    }

	function getState() public view returns (bool){
		return keyWasRevealed;
	}

}