// SPDX-License-Identifier: MIT
pragma solidity ^0.8.6;

import "./node_modules/@openzeppelin/contracts/utils/cryptography/ECDSA.sol";
import "./node_modules/@openzeppelin/contracts/security/ReentrancyGuard.sol";
import "./node_modules/@openzeppelin/contracts/utils/Create2.sol";
import "./node_modules/@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "./node_modules/@openzeppelin/contracts/token/ERC20/utils/SafeERC20.sol";

import "./node_modules/@openzeppelin/contracts/utils/cryptography/MerkleProof.sol";

contract UPC2 is ReentrancyGuard{
    
    using SafeERC20 for IERC20;

    // =====================================================
    //             ENUMS and STRUCTS
    // =====================================================
    enum ChannelStatus {ACTIVE, PAUSE, CLOSING, CLOSED}

    /* channel parameters set at the time of channel creation */
    struct ChannelParams{
        uint256 cid;                        // unique channel id
        uint256 chainId;
        address hubAddress;             // address of the hub
        address clientAddress;          // address of client
        uint256 claimDuration;          // time (in seconds) for claiming receipts and promises
        IERC20 tokenImplementation;     // e.g., address of the USDC contract
    }

    /* variables used for each of the two parties in this channel */
    struct Party{
        address addr;                       // address of the party
        uint256 deposit;                    // the amount that the party deposited to the contract
        uint256 rid;                        // receipt index
        uint256 credit;                     // the credit for this party from receipt and promises
        uint256 prevCredit;                 // previous credit value submitted by the party
        uint256 withdrawAmount;             // amount to for intermittent withdrawal
        bool receiptRegistered;             // has the party registered a receipt
        bool resumeOffchain;                // agree to continue off-chain
        bool finalizeClose;                 // is the party requesting to finalize close channel
        bytes32 acc;                        // accumulator for unresolved promises sent
    }

    /* receipt object from off-chain txs */
    struct Receipt{
        uint256 rid;                        // receipt index
        uint256 credit;                     // aggregate promise amounts resolved off-chain
        bytes32 acc;                        // merkle root of unresolved promises
    }

    /* promise object from off-chain txs */
    struct Promise{
        address sender;                     // address of sender
        address receiver;                   // address of receiver
        uint256 rid;                        // receipts index   
        uint256 salt;                       // salt for deploying the promise contract
        bytes contractByteCode;             // bytecode of the promise to deploy
    }

    /* signature object used for receipts and promises */
    struct Signature{
        uint8 v;
        bytes32 r;
        bytes32 s;
    }

    struct PromiseAddressReceiver{
        address addr;                       // address of the promise contract
        address receiver;                   // address of the receiver of the promise
    }

    // =====================================================
    //             CHANNEL VARIABLES
    // =====================================================
    ChannelParams channelParams;                    // channel parameters set at the constructor
    ChannelStatus channelStatus;                    // channel status
    uint256 channelExpiry;                          // linux time when channel expires
    Party hub; Party client;                        // two parties in the channel
    PromiseAddressReceiver[] unresolvedPromises;    // a list of registered promises to be resolved
    uint256 private constant unresolvedPromiseLimit = 2000;


    // =====================================================
    //             EVENTS
    // =====================================================
    event Deposit(address indexed from, uint256 indexed id, uint256 amount);
    event DeployPromise(address indexed from, uint256 indexed id, address promiseAddress);
    event SetClosing(address indexed from, uint256 indexed id, uint256 channelExpiry);
    event Close(address indexed from, uint256 indexed id);
    event WithdrawRequest(address indexed from, uint256 indexed id, uint256 amount);
    event Withdraw(uint256 indexed id, bool fullWithdrawal, uint256 clientAmount, uint256 hubAmount, uint256 clientDeposit, uint256 hubDeposit, uint256 clientPrevCredit, uint256 hubPrevCredit);

    // =====================================================
    //             CONTRACT FUNCTIONS
    // =====================================================
    constructor (ChannelParams memory _channelParams) {

        require(_channelParams.hubAddress != address(0) && _channelParams.clientAddress != address(0) && _channelParams.claimDuration != 0, "channelParams should be non-zero."); 
        channelParams = _channelParams;
        channelStatus = ChannelStatus.ACTIVE;
        hub.addr = _channelParams.hubAddress;
        client.addr = _channelParams.clientAddress;
    }


    /// @notice each party can deposit funds (msg.value) to the channel multiple times while channel is active
    function depositToken(uint256 amount) external nonReentrant onlyChannelParticipants {

        require(amount != 0, "Deposit amount should not be zero.");
        uint256 _balanceBefore = channelParams.tokenImplementation.balanceOf(address(this));
        channelParams.tokenImplementation.safeTransferFrom(msg.sender, address(this), amount);
        uint256 _actualAmount = channelParams.tokenImplementation.balanceOf(address(this)) - _balanceBefore;

        if (msg.sender == hub.addr) {

            hub.deposit += _actualAmount;
        }
        else // msg.sender == client.addr
        {
            client.deposit += _actualAmount;
        }
        emit Deposit(msg.sender, channelParams.cid, _actualAmount);
    }


    /// @notice registering the latest receipt received from the other party
    /// @param receipt        the latest receipt from offchain txs
    /// @param sig           signature of the other party on the values of receipt
    function registerReceipt(Receipt memory receipt, Signature memory sig) external onlyChannelParticipants channelActiveOrPauseOrClosing setPaused{
        require(verifyReceipt(receipt, sig, msg.sender));
        Party storage thisParty = _getParty(msg.sender);
        thisParty.rid = receipt.rid;
        thisParty.credit = receipt.credit;
        thisParty.acc = receipt.acc;
        thisParty.receiptRegistered = true;
    }

    function verifyReceipt(Receipt memory receipt, Signature memory sig, address sender) public view onlyChannelParticipants returns (bool) {
        Party memory thisParty = _getParty(sender);
        Party memory otherParty = _getOtherParty(sender);
        require(receipt.rid > thisParty.rid || (thisParty.rid == 0 && receipt.rid == 0));
        require(thisParty.receiptRegistered == false, "Receipt already registered");
        require(_sigVerify(otherParty.addr, _receiptHash(receipt), sig) || (receipt.rid == 0 && receipt.credit == 0), "signature of other-party on receipt does not verify");
        return true;
    }


    /// @notice registering a promise by deploying the promise contract; Sender of promise can register a promise as well as the receiver
    /// @param _promise    the latest receipt from off-chain txs
    /// @param sig           signature of the other party on the promise
    /// @param memProof    the merkle proof consisting the path from leaf to root
    function registerPromise(Promise memory _promise, Signature memory sig, bytes32[] memory memProof) external onlyChannelParticipants channelActiveOrPauseOrClosing setClosing{
        address promiseAddress = verifyPromise(_promise, sig, memProof, _getParty(msg.sender));
        _deploy(_promise.contractByteCode, _promise.salt); // deploy the bytecode of this promise
        unresolvedPromises.push(PromiseAddressReceiver(promiseAddress, _promise.receiver)); // keep track of the registered promises in unresolvedPromises list
        emit DeployPromise(msg.sender, channelParams.cid, promiseAddress);
    }

    function verifyPromise(Promise memory _promise, Signature memory sig, bytes32[] memory memProof, Party memory thisParty) public view onlyChannelParticipants returns (address){
        require( (_promise.sender == client.addr && _promise.receiver == hub.addr) || (_promise.sender == hub.addr && _promise.receiver == client.addr) , "sender and receiver of promise are not correctly set");
        require(unresolvedPromises.length < unresolvedPromiseLimit, "Unresolved promises array is maxed out.");
        address promiseAddress = getAddress(_promise.contractByteCode, _promise.salt);
        if (thisParty.addr == _promise.receiver){
            require(_sigVerify(_promise.sender, _promiseHash(_promise, promiseAddress), sig), "signature of promise sender does not verify");
            require(thisParty.receiptRegistered == true, "receipt of the receiver needs to be registered first (to make sure this promise is not being double spent)");
            if((_promise.rid < thisParty.rid) && thisParty.rid != 0) // to prevent double spending, check membership proof of promise inside the accumulator submitted in registerReceipt
                require(MerkleProof.verify(memProof, thisParty.acc, keccak256(abi.encode(promiseAddress))), "membership proof of the promise inside the accumulator not verified");
        }
        return promiseAddress;
    }


    // @notice intermittent withdrawal from the contract based on the registered Receipt
	/// @param amount    the amount that the party wants to deposit
    function withdrawNotClosing(uint256 amount) external nonReentrant onlyChannelParticipants channelActiveOrPause setPaused{
        Party storage thisParty = _getParty(msg.sender);
        thisParty.withdrawAmount = amount;
        thisParty.resumeOffchain = true;
        emit WithdrawRequest(msg.sender, channelParams.cid, amount);

        if ((hub.resumeOffchain && client.resumeOffchain) || block.timestamp >= channelExpiry ){
            hub.deposit = hub.deposit + (hub.credit - hub.prevCredit) - (client.credit - client.prevCredit);
            client.deposit = client.deposit + (client.credit - client.prevCredit) - (hub.credit - hub.prevCredit);
            if (hub.deposit > 0) {
                if (hub.withdrawAmount <= hub.deposit){
                    hub.deposit = hub.deposit - hub.withdrawAmount;
                    channelParams.tokenImplementation.transfer(hub.addr, hub.withdrawAmount);
                }
            }
            if (client.deposit > 0) {
                if (client.withdrawAmount <= client.deposit){
                    client.deposit = client.deposit - client.withdrawAmount;
                    channelParams.tokenImplementation.transfer(client.addr, client.withdrawAmount);
                }
            }
            emit Withdraw(channelParams.cid, false, client.withdrawAmount, hub.withdrawAmount, client.deposit, hub.deposit, client.credit, hub.credit);
            resumeChannel();
        }
    }


    /// @notice finalizing the closing of the channel;
    /// @notice When each party finished all their receipt/promise registrations they can invoke this function to cooperatively close the channel before channel expiry
    function close() external onlyChannelParticipants channelActiveOrPauseOrClosing setClosing{
        if (msg.sender == hub.addr)
            hub.finalizeClose = true;
        else
            client.finalizeClose = true;
        if (hub.finalizeClose && client.finalizeClose){
            channelStatus = ChannelStatus.CLOSED;
            emit Close(msg.sender, channelParams.cid);
        } 
    }


    /// @notice upon expiry of the channel or cooperative closing, anyone can call to withdraw the settled amounts for each party
    function withdrawClosing() external nonReentrant channelClosed onlyChannelParticipants{
        _resolvePromises();

        require(hub.deposit + client.deposit >= hub.credit + client.credit, "Trying to withdraw more than you have");

        hub.deposit = hub.deposit + hub.credit - client.credit;
        client.deposit = client.deposit + client.credit - hub.credit;
        hub.credit = 0;
        client.credit = 0;

        if (hub.deposit > 0) {
            channelParams.tokenImplementation.transfer(hub.addr, hub.deposit);
            hub.deposit = 0;
        }

        if (client.deposit > 0) {
            channelParams.tokenImplementation.transfer(client.addr, client.deposit);
            client.deposit = 0;
        }

        if (hub.deposit == 0 && client.deposit == 0) {
            uint256 contractBalance = channelParams.tokenImplementation.balanceOf(address(this));
            if(contractBalance > 0) {
                channelParams.tokenImplementation.transfer(hub.addr, contractBalance);
            }
            emit Withdraw(channelParams.cid, true, client.deposit, hub.deposit, 0, 0, 0, 0);
            selfdestruct(payable(msg.sender));
        }
    }


/// @notice this function is called at the time of withdrawal to iterate through all the registered promises and resolve the final amount
    function _resolvePromises() internal {
        uint256 i = 0;
         while (i < unresolvedPromises.length) { // may want to batch this; to avoid running out of gas
            // try{
                _resolvePromise(unresolvedPromises[i]);
            // }
            // catch{
            //     console.log("Catch on i = %s" , i);
            //     //TODO: emit similar error here.
            // } 
            i++;
        }
        delete unresolvedPromises;
    }
    

    /// @notice this function is called upon closing the channel to invoke the resolve() in the promise contract
    /// @notice the resolve() for each promise is only called once at the time of closing the channel and the amount returned is added to the receiver's credit
    /// @param _promise    is a promise address and receiver pair to resolve the final amount
    function _resolvePromise(PromiseAddressReceiver memory _promise) internal {
        (bool success, bytes memory data) = _promise.addr.call(abi.encodeWithSignature("resolve()"));
        require(success, "Failed to resolve promise");
        (uint256 amount) = abi.decode(data, (uint256));
        Party storage receiver = _getParty(_promise.receiver);
        receiver.credit += amount;
    }

    // =====================================================
    //             MODIFIERS
    // =====================================================
    modifier onlyChannelParticipants {
        require((msg.sender == hub.addr) || (msg.sender == client.addr), "Sender is not a channel participant");
        _;}

    modifier channelActiveOrPause {
        require(channelStatus == ChannelStatus.ACTIVE || channelStatus == ChannelStatus.PAUSE, "Channel is not active or paused");
        _;}

    modifier channelActiveOrPauseOrClosing {
        require(channelStatus == ChannelStatus.ACTIVE || channelStatus == ChannelStatus.PAUSE || channelStatus == ChannelStatus.CLOSING, "Channel is not active or paused");
        _;}

    modifier channelActiveOrClosing {
        require(channelStatus == ChannelStatus.ACTIVE || channelStatus == ChannelStatus.CLOSING, "Channel is not active or closing");
        _;}

    modifier channelClosed {
        require(channelStatus == ChannelStatus.CLOSED || (channelStatus == ChannelStatus.CLOSING && block.timestamp >= channelExpiry), "Channel is not closed");
        _;}




    modifier setPaused {
        _;
        if (channelStatus == ChannelStatus.ACTIVE){
            channelExpiry = block.timestamp + channelParams.claimDuration;
            channelStatus = ChannelStatus.PAUSE;
        }
    }
      
    modifier setClosing {
        _;
        if (channelStatus != ChannelStatus.CLOSING){
            channelExpiry = block.timestamp + channelParams.claimDuration;
            channelStatus = ChannelStatus.CLOSING;
            emit SetClosing(msg.sender, channelParams.cid, channelExpiry);
        }
        else if (channelStatus == ChannelStatus.CLOSING){
            require(block.timestamp < channelExpiry, "Expiry has passed");
        }
    }    

    // =====================================================
    //             HELPER FUNCTIONS
    // =====================================================

    function _receiptHash(Receipt memory receipt) public view returns(bytes32){
        return keccak256(abi.encode(channelParams.chainId, channelParams.cid, receipt.rid, receipt.credit, receipt.acc));
    }

    function _promiseHash(Promise memory _promise, address promiseAddress) public view returns(bytes32){
        return keccak256(abi.encode(channelParams.chainId, channelParams.cid, _promise.rid, _promise.sender, _promise.receiver, promiseAddress));
    }

    function _sigVerify(address ver, bytes32 message, Signature memory sig) public pure returns (bool){

        (address signer, ) =  ECDSA.tryRecover(keccak256(abi.encodePacked("\x19Ethereum Signed Message:\n32", message)), sig.v, sig.r, sig.s);
        return ver == signer;
    }


    function getParams() external view returns(ChannelParams memory) {
        return channelParams;
    }


    function getState() external view returns(ChannelStatus, Party memory, Party memory, uint256) {
        return (channelStatus, hub, client, channelExpiry);
    }


    function _getParty(address addr) private view returns (Party storage){
        if (addr == hub.addr)
            return hub;
        else
            return client;
    }

    function _getOtherParty(address addr) private view returns (Party storage){
        if (addr == hub.addr)
            return client;
        else
            return hub;
    }

    function getAddress(bytes memory bytecode, uint256 salt) public view returns (address) {
       bytes32 hash = keccak256(abi.encodePacked(bytes1(0xff), address(this), salt, keccak256(bytecode)));
        // NOTE: cast last 20 bytes of hash to address
        return address(uint160(uint(hash)));
    }

    function getUnresolvedPromises() external view returns(PromiseAddressReceiver[] memory) {
        return unresolvedPromises;
    }

    function deploy(bytes memory bytecode, uint256 salt) private {
        address addr;
        assembly {
            addr := create2(callvalue(), add(bytecode, 0x20), mload(bytecode), salt)
            if iszero(extcodesize(addr)) {
                revert(0, 0)
            }
        }
    }

   function _deploy(bytes memory bytecode, uint256 salt) private {
       address addr;
       addr = Create2.deploy(msg.value, bytes32(salt), bytecode);
   }

    function resumeChannel() private {
        //reset hub variables
        hub.resumeOffchain = false;
        hub.receiptRegistered = false;
        hub.withdrawAmount = 0;
        hub.prevCredit = hub.credit;
        //reset client variables
        client.resumeOffchain = false;
        client.receiptRegistered = false;
        client.withdrawAmount = 0;
        client.prevCredit = client.credit;
        channelStatus = ChannelStatus.ACTIVE;
    }

    function checkProof(bytes32[] memory memProof, bytes32 root, bytes32 hash) external pure returns (bool) {
        return MerkleProof.verify(memProof, root, hash);
    }
}