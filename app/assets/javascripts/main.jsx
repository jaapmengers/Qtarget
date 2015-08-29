var Main = React.createClass({
  getInitialState: function() {
    return { msg: "Hello, World!" };
  },
  render: function(){
    return (
      <div>Message: {this.state.msg}</div>
    )
  }
});

component = React.render(<Main />, document.getElementById("main"));
