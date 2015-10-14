var Main = React.createClass({
  getInitialState: function() {
    return {state : 'ranking'};
  },
  render: function(){
    if(this.state.state === 'ranking') {
      return (
        <Ranking />
      )
    }
  }
});

var RankingRow = React.createClass({
  render: function() {
    return (<li>{this.props.name}</li>)
  }
});

var Ranking = React.createClass({
  getInitialState: function(){
    return { ranking: [{
      rank: 1, name: 'Jaapem'
    }, {
      rank: 2, name: 'Joel',
    }, {
      rank: 3, name: 'Meindert'
    }]
    }
  },
  render: function() {
    var rows = this.state.ranking.map(function(x){
      return <RankingRow rank={x.rank} name={x.name} />
    });

    return (<ol>{rows}</ol>);
  }
});


component = React.render(<Main />, document.getElementById("main"));
