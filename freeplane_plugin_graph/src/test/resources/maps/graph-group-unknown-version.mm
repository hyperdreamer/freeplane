<map version="freeplane 1.12.0" unrelated_map_attribute="preserve-map">
  <map_unknown_one order="1" map_extra="alpha"/>
  <map_unknown_two order="2" map_extra="beta"/>
  <node TEXT="fixture root" ID="ID_ROOT" root_attribute="preserve-root">
    <root_unknown_one order="1" root_extra="alpha"/>
    <root_unknown_two order="2" root_extra="beta"/>
    <node TEXT="unsupported leaf" ID="ID_UNSUPPORTED" unsupported_attribute="preserve-unsupported">
      <unknown_before order="1" unknown_extra="before"/>
      <graph_group version="99" extra="preserve" raw="keep">
        <nested flag="yes">payload</nested>
        <nested_second flag="no">tail</nested_second>
      </graph_group>
      <unknown_after order="2" unknown_extra="after"/>
    </node>
    <node TEXT="plain leaf" ID="ID_PLAIN" plain_attribute="preserve-plain">
      <plain_unknown_one order="1" plain_extra="one"/>
      <plain_unknown_two order="2" plain_extra="two"/>
    </node>
  </node>
</map>
