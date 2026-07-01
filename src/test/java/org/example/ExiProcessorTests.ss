╔═ decodeWithPositionReportMatchesSnapshot ═╗
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<ns4:PositionReport xmlns:ns4="https://www.vdl.afrl.af.mil/programs/oam" xmlns:ns3="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
    <ns4:SecurityInformation>
        <ns4:Classification>U</ns4:Classification>
        <ns4:OwnerProducer>
            <ns4:GovernmentIdentifier>USA</ns4:GovernmentIdentifier>
        </ns4:OwnerProducer>
    </ns4:SecurityInformation>
    <ns4:MessageHeader>
        <ns4:SystemID>
            <ns4:UUID>a1b2c3d4-e5f6-7890-abcd-ef1234567890</ns4:UUID>
            <ns4:DescriptiveLabel>Sender System</ns4:DescriptiveLabel>
        </ns4:SystemID>
        <ns4:Timestamp>2026-06-27T12:00:00Z</ns4:Timestamp>
        <ns4:SchemaVersion>002.5</ns4:SchemaVersion>
        <ns4:Mode>EXERCISE</ns4:Mode>
    </ns4:MessageHeader>
    <ns4:MessageData>
        <ns4:SystemID>
            <ns4:UUID>b2c3d4e5-f6a7-8901-bcde-f12345678901</ns4:UUID>
            <ns4:DescriptiveLabel>Reporting Platform</ns4:DescriptiveLabel>
        </ns4:SystemID>
        <ns4:DisplayName>UAV-ALPHA-01</ns4:DisplayName>
        <ns4:Source>ACTUAL</ns4:Source>
        <ns4:CurrentOperatingDomain>AIR</ns4:CurrentOperatingDomain>
        <ns4:InertialState>
            <ns4:Position>
                <ns4:Latitude>679042E-6</ns4:Latitude>
                <ns4:Longitude>-134435E-5</ns4:Longitude>
                <ns4:Altitude>3048E-1</ns4:Altitude>
                <ns4:Timestamp>2026-06-27T12:00:00Z</ns4:Timestamp>
            </ns4:Position>
        </ns4:InertialState>
        <ns4:Timestamp>2026-06-27T12:00:00Z</ns4:Timestamp>
    </ns4:MessageData>
</ns4:PositionReport>
╔═ encodeWithPositionReportMatchesSnapshot ═╗
ExiWithLen(len=139, exi=806fc403ac7c09a2290e64a1415358e04254104653a02b610c8531d0950403d9c59d122d200cecd3a91668069b6c0005c000704082010e1404d21cc942d4c0b1e280254110253a541610c8531d095044028ca2d09f4aa499d1c0309906a469f4a68039aa16c350ac60a108d20440001057253051a29a08820740bc00834db60002e013a069b6c0005c0100)
╔═ [end of file] ═╗
