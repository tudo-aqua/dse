<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<graphml xmlns="http://graphml.graphdrawing.org/xmlns" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">

  <key attr.name="architecture" attr.type="string" for="graph" id="architecture"/>
  <key attr.name="creationtime" attr.type="string" for="graph" id="creationtime"/>
  <key attr.name="producer" attr.type="string" for="graph" id="producer"/>
  <key attr.name="programFile" attr.type="string" for="graph" id="programfile"/>
  <key attr.name="programHash" attr.type="string" for="graph" id="programhash"/>
  <key attr.name="sourcecodeLanguage" attr.type="string" for="graph" id="sourcecodelang"/>
  <key attr.name="specification" attr.type="string" for="graph" id="specification"/>
  <key attr.name="witness-type" attr.type="string" for="graph" id="witness-type"/>

  <key attr.name="invariant" attr.type="string" for="node" id="invariant"/>
  <key attr.name="isEntryNode" attr.type="boolean" for="node" id="entry"><default>false</default></key>

  <key attr.name="threadId" attr.type="int" for="edge" id="threadId"><default>0</default></key>

  <graph edgedefault="directed">
    <data key="architecture">32bit</data>
    <data key="creationtime">$creationtime$</data>
    <data key="producer">GDart-LLVM</data>
    <data key="programfile">$programfile$</data>
    <data key="programhash">$programhash$</data>
    <data key="sourcecodelang">C</data>
    <data key="specification">CHECK( init(main()), LTL(G ! call(reach_error())) )</data>
    <data key="witness-type">correctness_witness</data>

    <node id="n0">
      <data key="entry">true</data>
      <data key="invariant">1</data>
    </node>
    <node id="n1">
      <data key="invariant">1</data>
    </node>

    <edge source="n0" target="n1">
      <data key="threadId">0</data>
    </edge>

  </graph>
</graphml>