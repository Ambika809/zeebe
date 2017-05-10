package org.camunda.tngp.broker.clustering.raft.message;

import static org.camunda.tngp.clustering.raft.LeaveRequestEncoder.hostHeaderLength;
import static org.camunda.tngp.clustering.raft.LeaveRequestEncoder.partitionIdNullValue;
import static org.camunda.tngp.clustering.raft.LeaveRequestEncoder.topicNameHeaderLength;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.clustering.raft.Member;
import org.camunda.tngp.clustering.raft.LeaveRequestDecoder;
import org.camunda.tngp.clustering.raft.LeaveRequestEncoder;
import org.camunda.tngp.clustering.raft.MessageHeaderDecoder;
import org.camunda.tngp.clustering.raft.MessageHeaderEncoder;
import org.camunda.tngp.util.buffer.BufferReader;
import org.camunda.tngp.util.buffer.BufferWriter;

public class LeaveRequest implements BufferReader, BufferWriter
{
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final LeaveRequestEncoder bodyEncoder = new LeaveRequestEncoder();

    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final LeaveRequestDecoder bodyDecoder = new LeaveRequestDecoder();

    protected DirectBuffer topicName = new UnsafeBuffer(0, 0);
    protected int partitionId = partitionIdNullValue();

    protected final Member member = new Member();
    protected boolean isMemberAvailable = false;

    public DirectBuffer topicName()
    {
        return topicName;
    }

    public LeaveRequest topicName(final DirectBuffer topicName)
    {
        this.topicName.wrap(topicName);
        return this;
    }

    public int partitionId()
    {
        return partitionId;
    }

    public LeaveRequest partitionId(final int partitionId)
    {
        this.partitionId = partitionId;
        return this;
    }

    public Member member()
    {
        return member;
    }

    public LeaveRequest member(final Member member)
    {
        isMemberAvailable = false;
        this.member.endpoint().reset();
        if (member != null)
        {
            this.member.endpoint().wrap(member.endpoint());
            isMemberAvailable = true;
        }

        return this;
    }

    @Override
    public int getLength()
    {
        int size = headerEncoder.encodedLength() +
                bodyEncoder.sbeBlockLength() +
                topicNameHeaderLength() +
                topicName.capacity() +
                hostHeaderLength();

        if (isMemberAvailable)
        {
            size += member.endpoint().hostLength();
        }

        return size;
    }

    @Override
    public void write(final MutableDirectBuffer buffer, int offset)
    {
        headerEncoder.wrap(buffer, offset)
            .blockLength(bodyEncoder.sbeBlockLength())
            .templateId(bodyEncoder.sbeTemplateId())
            .schemaId(bodyEncoder.sbeSchemaId())
            .version(bodyEncoder.sbeSchemaVersion());

        offset += headerEncoder.encodedLength();

        bodyEncoder.wrap(buffer, offset)
            .partitionId(partitionId)
            .term(-1)
            .port(member.endpoint().port())
            .putTopicName(topicName, 0, topicName.capacity())
            .putHost(member.endpoint().getHostBuffer(), 0, member.endpoint().hostLength());
    }

    @Override
    public void wrap(final DirectBuffer buffer, int offset, final int length)
    {
        final int frameEnd = offset + length;

        headerDecoder.wrap(buffer, offset);
        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        partitionId = bodyDecoder.partitionId();

        final int topicNameLength = bodyDecoder.topicNameLength();
        final int topicNameOffset = bodyDecoder.limit() + topicNameHeaderLength();
        topicName.wrap(buffer, topicNameOffset, topicNameLength);

        bodyDecoder.limit(topicNameOffset + topicNameLength);

        isMemberAvailable = false;
        member.endpoint().reset();

        final int hostLength = bodyDecoder.hostLength();
        if (hostLength > 0)
        {
            member.endpoint().port(bodyDecoder.port());

            final MutableDirectBuffer endpointBuffer = member.endpoint().getHostBuffer();
            member.endpoint().hostLength(hostLength);
            bodyDecoder.getHost(endpointBuffer, 0, hostLength);

            isMemberAvailable = true;
        }
        else
        {
            // skip host header in decoder
            bodyDecoder.limit(bodyDecoder.limit() + hostHeaderLength());
        }

        assert bodyDecoder.limit() == frameEnd : "Decoder read only to position " + bodyDecoder.limit() + " but expected " + frameEnd + " as final position";
    }

    public void reset()
    {
        topicName.wrap(0, 0);
        partitionId = partitionIdNullValue();
        isMemberAvailable = false;
        member.endpoint().reset();
    }
}
