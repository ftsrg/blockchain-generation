
% Ordering and Endorsement Services
service(Service) :- endorsingnode(Service).
% service(Service) :- orderingnode(Service).

% Selecting faulty hostst and services
{hostDown(Host):host(Host)}.
{serviceDown(Service):service(Service)}.

% Fault weights
:~ serviceDown(X). [1,serviceDown(X)]
:~ hostDown(X). [2,host(X)]

%
% Ordering
%

% All Ordering
allOrdering(Org, Host, OrderingNode, Channel) :-
    hosts(Org, Host),
    participatesIn(Org, Channel),
    nodes(Host, OrderingNode),
    peer(OrderingNode, Channel).

allOrgChannel(Org, Channel) :- allOrdering(Org, Host, OrderingNode, Channel).
allOrgPerChannel(Channel, X) :- channel(Channel), X = #count{Org, Channel: allOrgChannel(Org, Channel)}.

% Active Ordering Services/Host
activeOrdering(Org, Host, OrderingNode, Channel) :-
    hosts(Org, Host),
    participatesIn(Org, Channel),
    nodes(Host, OrderingNode),
    peer(OrderingNode, Channel),
    not hostDown(Host),
    not serviceDown(OrderingNode).

% Active participation of Organizations in Channels
orderingOrgChannel(Org, Channel) :- activeOrdering(Org,Host,OrderingNode,Channel).
% Number of different active Organizations in Channels
numberofOrgperChannel(Channel, X) :- channel(Channel), X = #count{Org, Channel: orderingOrgChannel(Org, Channel)}.


% If there is less than 2 active Orgs/Channel, it is a violation, if it was not an already dangling channel.
violation(Channel, X) :- numberofOrgperChannel(Channel, X), X<2, allOrgPerChannel(Channel, Y), Y>1.
:- not violation(_,_). % Checking the cases where active faults violated the requirement.

% #show numberofOrgperChannel/2.
% #show allOrdering/4.
% #show orderingOrgChannel/2.
#show violation/2.
#show hostDown/1.
#show serviceDown/1.


