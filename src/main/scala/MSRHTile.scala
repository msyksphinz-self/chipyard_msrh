//******************************************************************************
// Copyright (c) 2019 - 2019, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE and LICENSE.SiFive for license details.
//------------------------------------------------------------------------------

//------------------------------------------------------------------------------
//------------------------------------------------------------------------------
// MSRH Tile Wrapper
//------------------------------------------------------------------------------
//------------------------------------------------------------------------------

package msrh

import chisel3._
import chisel3.util._
import chisel3.experimental.{IntParam, StringParam}

import scala.collection.mutable.{ListBuffer}

import freechips.rocketchip.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.diplomaticobjectmodel.logicaltree.{LogicalTreeNode}
import freechips.rocketchip.rocket._
import freechips.rocketchip.subsystem.{RocketCrossingParams}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.util._
import freechips.rocketchip.tile._

case object MSRHTilesKey extends Field[Seq[MSRHTileParams]](Nil)

case class MSRHCoreParams(
  bootFreqHz: BigInt = BigInt(1700000000),
  rasEntries: Int = 4,
  btbEntries: Int = 16,
  bhtEntries: Int = 16,
  enableToFromHostCaching: Boolean = false,
) extends CoreParams {
  /* DO NOT CHANGE BELOW THIS */
  val useVM: Boolean = true
  val useUser: Boolean = true
  val useSupervisor: Boolean = false
  val useDebug: Boolean = true
  val useAtomics: Boolean = true
  val useAtomicsOnlyForIO: Boolean = false // copied from Rocket
  val useCompressed: Boolean = true
  override val useVector: Boolean = false
  val useSCIE: Boolean = false
  val useRVE: Boolean = false
  val mulDiv: Option[MulDivParams] = Some(MulDivParams()) // copied from Rocket
  val fpu: Option[FPUParams] = Some(FPUParams()) // copied fma latencies from Rocket
  val nLocalInterrupts: Int = 0
  val nPMPs: Int = 0 // TODO: Check
  val pmpGranularity: Int = 4 // copied from Rocket
  val nBreakpoints: Int = 0 // TODO: Check
  val useBPWatch: Boolean = false
  val nPerfCounters: Int = 29
  val haveBasicCounters: Boolean = true
  val haveFSDirty: Boolean = false
  val misaWritable: Boolean = false
  val haveCFlush: Boolean = false
  val nL2TLBEntries: Int = 512 // copied from Rocket
  val mtvecInit: Option[BigInt] = Some(BigInt(0)) // copied from Rocket
  val mtvecWritable: Boolean = true // copied from Rocket
  val instBits: Int = if (useCompressed) 16 else 32
  val lrscCycles: Int = 80 // copied from Rocket
  val decodeWidth: Int = 1 // TODO: Check
  val fetchWidth: Int = 1 // TODO: Check
  val retireWidth: Int = 2
}

// TODO: BTBParams, DCacheParams, ICacheParams are incorrect in DTB... figure out defaults in MSRH and put in DTB
case class MSRHTileParams(
  name: Option[String] = Some("MSRH_tile"),
  hartId: Int = 0,
  beuAddr: Option[BigInt] = None,
  blockerCtrlAddr: Option[BigInt] = None,
  btb: Option[BTBParams] = Some(BTBParams()),
  core: MSRHCoreParams = MSRHCoreParams(),
  dcache: Option[DCacheParams] = Some(DCacheParams()),
  icache: Option[ICacheParams] = Some(ICacheParams()),
  boundaryBuffers: Boolean = false,
  trace: Boolean = false
  ) extends TileParams

class MSRHTile(
  val MSRHParams: MSRHTileParams,
  crossing: ClockCrossingType,
  lookup: LookupByHartIdImpl,
  q: Parameters,
  logicalTreeNode: LogicalTreeNode)
  extends BaseTile(MSRHParams, crossing, lookup, q)
  with SinksExternalInterrupts
  with SourcesExternalNotifications
{
  /**
   * Setup parameters:
   * Private constructor ensures altered LazyModule.p is used implicitly
   */
  def this(params: MSRHTileParams, crossing: RocketCrossingParams, lookup: LookupByHartIdImpl, logicalTreeNode: LogicalTreeNode)(implicit p: Parameters) =
    this(params, crossing.crossingType, lookup, p, logicalTreeNode)

  val intOutwardNode = IntIdentityNode()
  val slaveNode = TLIdentityNode()
  val masterNode = visibilityNode

  tlOtherMastersNode := tlMasterXbar.node
  masterNode :=* tlOtherMastersNode
  DisableMonitors { implicit p => tlSlaveXbar.node :*= slaveNode }

  val cpuDevice: SimpleDevice = new SimpleDevice("cpu", Seq("eth-zurich,MSRH", "riscv")) {
    override def parent = Some(ResourceAnchors.cpus)
    override def describe(resources: ResourceBindings): Description = {
      val Description(name, mapping) = super.describe(resources)
      Description(name, mapping ++
                        cpuProperties ++
                        nextLevelCacheProperty ++
                        tileProperties)
    }
  }

  ResourceBinding {
    Resource(cpuDevice, "reg").bind(ResourceAddress(hartId))
  }

  override def makeMasterBoundaryBuffers(implicit p: Parameters) = {
    if (!MSRHParams.boundaryBuffers) super.makeMasterBoundaryBuffers
    else TLBuffer(BufferParams.none, BufferParams.flow, BufferParams.none, BufferParams.flow, BufferParams(1))
  }

  override def makeSlaveBoundaryBuffers(implicit p: Parameters) = {
    if (!MSRHParams.boundaryBuffers) super.makeSlaveBoundaryBuffers
    else TLBuffer(BufferParams.flow, BufferParams.none, BufferParams.none, BufferParams.none, BufferParams.none)
  }

  override lazy val module = new MSRHTileModuleImp(this)

  /**
   * Setup AXI4 memory interface.
   * THESE ARE CONSTANTS.
   */
  val portName = "MSRH-mem-port-axi4"
  val idBits = 4
  val beatBytes = masterPortBeatBytes
  val sourceBits = 1 // equiv. to userBits (i think)

  val cacheClientParameters = Seq(TLClientParameters(
    name          = s"Core DCache",
    sourceId      = IdRange(0, 1),
    supportsProbe = TransferSizes(8, 8)))

  val memTLNode = TLClientNode(Seq(TLClientPortParameters(
    cacheClientParameters)))

//  val memAXI4Node = AXI4MasterNode(
//    Seq(AXI4MasterPortParameters(
//      masters = Seq(AXI4MasterParameters(
//        name = portName,
//        id = IdRange(0, 1 << idBits))))))

  tlMasterXbar.node := memTLNode

//  val memoryTap = TLIdentityNode()
//  (tlMasterXbar.node
//    := memoryTap
//    := TLBuffer()
//    := TLFIFOFixer(TLFIFOFixer.all) // fix FIFO ordering
//    := TLWidthWidget(beatBytes) // reduce size of TL
//    := AXI4ToTL() // convert to TL
//    := AXI4UserYanker(Some(2)) // remove user field on AXI interface. need but in reality user intf. not needed
//    := AXI4Fragmenter() // deal with multi-beat xacts
//    := memAXI4Node)

  def connectMSRHInterrupts(debug: Bool, msip: Bool, mtip: Bool, m_s_eip: UInt) {
    val (interrupts, _) = intSinkNode.in(0)
    debug := interrupts(0)
    msip := interrupts(1)
    mtip := interrupts(2)
    m_s_eip := Cat(interrupts(4), interrupts(3))
  }
}

class MSRHTileModuleImp(outer: MSRHTile) extends BaseTileModuleImp(outer){
  // annotate the parameters
  Annotated.params(this, outer.MSRHParams)

  val debugBaseAddr = BigInt(0x0) // CONSTANT: based on default debug module
  val debugSz = BigInt(0x1000) // CONSTANT: based on default debug module
  val tohostAddr = BigInt(0x80001000L) // CONSTANT: based on default sw (assume within extMem region)
  val fromhostAddr = BigInt(0x80001040L) // CONSTANT: based on default sw (assume within extMem region)

  // have the main memory, bootrom, debug regions be executable
  val executeRegionBases = Seq(p(ExtMem).get.master.base,      p(BootROMParams).address, debugBaseAddr, BigInt(0x0), BigInt(0x0))
  val executeRegionSzs   = Seq(p(ExtMem).get.master.size, BigInt(p(BootROMParams).size),       debugSz, BigInt(0x0), BigInt(0x0))
  val executeRegionCnt   = executeRegionBases.length

  // have the main memory be cached, but don't cache tohost/fromhost addresses
  // TODO: current cache subsystem can only support 1 cacheable region... so cache AFTER the tohost/fromhost addresses
  val wordOffset = 0x40
  val (cacheableRegionBases, cacheableRegionSzs) = if (outer.MSRHParams.core.enableToFromHostCaching) {
    val bases = Seq(p(ExtMem).get.master.base, BigInt(0x0), BigInt(0x0), BigInt(0x0), BigInt(0x0))
    val sizes   = Seq(p(ExtMem).get.master.size, BigInt(0x0), BigInt(0x0), BigInt(0x0), BigInt(0x0))
    (bases, sizes)
  } else {
    val bases = Seq(                                                          fromhostAddr + 0x40,              p(ExtMem).get.master.base, BigInt(0x0), BigInt(0x0), BigInt(0x0))
    val sizes = Seq(p(ExtMem).get.master.size - (fromhostAddr + 0x40 - p(ExtMem).get.master.base), tohostAddr - p(ExtMem).get.master.base, BigInt(0x0), BigInt(0x0), BigInt(0x0))
    (bases, sizes)
  }
  val cacheableRegionCnt   = cacheableRegionBases.length

  // Add 2 to account for the extra clock and reset included with each
  // instruction in the original trace port implementation. These have since
  // been removed from TracedInstruction.
  val traceInstSz = (new freechips.rocketchip.rocket.TracedInstruction).getWidth + 2

  // connect the MSRH core
  val core = Module(new MSRHCoreBlackbox(
    // general core params
    xLen = p(XLen),
  ))

  core.io.i_clk := clock
  core.io.i_reset_n := ~reset.asBool
  // core.io.boot_addr_i := constants.reset_vector
  // core.io.hart_id_i := constants.hartid

  // outer.connectMSRHInterrupts(core.io.debug_req_i, core.io.ipi_i, core.io.time_irq_i, core.io.irq_i)

//  if (outer.MSRHParams.trace) {
//    // unpack the trace io from a UInt into Vec(TracedInstructions)
//    //outer.traceSourceNode.bundle <> core.io.trace_o.asTypeOf(outer.traceSourceNode.bundle)
//
//    for (w <- 0 until outer.MSRHParams.core.retireWidth) {
//      outer.traceSourceNode.bundle(w).valid     := core.io.trace_o(traceInstSz*w + 2)
//      outer.traceSourceNode.bundle(w).iaddr     := core.io.trace_o(traceInstSz*w + 42, traceInstSz*w + 3)
//      outer.traceSourceNode.bundle(w).insn      := core.io.trace_o(traceInstSz*w + 74, traceInstSz*w + 43)
//      outer.traceSourceNode.bundle(w).priv      := core.io.trace_o(traceInstSz*w + 77, traceInstSz*w + 75)
//      outer.traceSourceNode.bundle(w).exception := core.io.trace_o(traceInstSz*w + 78)
//      outer.traceSourceNode.bundle(w).interrupt := core.io.trace_o(traceInstSz*w + 79)
//      outer.traceSourceNode.bundle(w).cause     := core.io.trace_o(traceInstSz*w + 87, traceInstSz*w + 80)
//      outer.traceSourceNode.bundle(w).tval      := core.io.trace_o(traceInstSz*w + 127, traceInstSz*w + 88)
//    }
//  } else {
    outer.traceSourceNode.bundle := DontCare
    outer.traceSourceNode.bundle map (t => t.valid := false.B)
  // }

  val tl_opcode = Wire(UInt(3.W))
  tl_opcode := TLMessages.Get
  switch (core.io.o_ic_req_cmd) {
    is(0.U) { tl_opcode := TLMessages.Get }
    is(1.U) { tl_opcode := TLMessages.PutFullData }
  }

  val beatBytes = p(SystemBusKey).beatBytes

  // connect the axi interface
  outer.memTLNode.out foreach { case (out, edgeOut) =>
    out.a.valid        := core.io.o_ic_req_valid
    out.a.bits.opcode  := tl_opcode
    out.a.bits.param   := 0.U
    out.a.bits.size    := log2Ceil(beatBytes).U
    out.a.bits.source  := core.io.o_ic_req_tag
    out.a.bits.address := core.io.o_ic_req_addr
    // out.a.bits.user    := 0.U
    // out.a.bits.echo    := 0.U
    out.a.bits.mask    := Fill(beatBytes * 8, 1.U(1.W))
    out.a.bits.data    := 0.U
    out.a.bits.corrupt := 0.U
    core.io.i_ic_req_ready     := out.a.ready

    core.io.i_ic_resp_valid := out.d.valid
    core.io.i_ic_resp_tag := out.d.bits.source
    core.io.i_ic_resp_data := out.d.bits.data
    out.d.ready := core.io.o_ic_resp_ready
  }
}
