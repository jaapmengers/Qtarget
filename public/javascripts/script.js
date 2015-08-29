init = function(socketUrl){
  console.log(init, socketUrl);

  var ws = new WebSocket(socketUrl);
  ws.onopen = function(){
      console.log("connected");
      var iets = JSON.stringify({"text": "Message to send"});
      ws.send(iets);
  };

  ws.onmessage = function (evt){
      var received_msg = JSON.parse(evt.data);
      component.setState({msg: received_msg.text});
  };

  ws.onclose = function(){
      // websocket is closed.
      console.error("Connection is closed...");
  };
};