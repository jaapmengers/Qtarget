start = function()
{
  console.log("Started")
  ws.send("START");
};

init = function(socketUrl){
  ws = new WebSocket(socketUrl);
  ws.onopen = function(){
    console.log("connected");
  };

  ws.onmessage = function (evt){
    console.log("Message " + evt.data);
    component.setState({msg: received_msg.text});
  };

  ws.onclose = function(){
    console.error("Connection is closed...");
  };
};