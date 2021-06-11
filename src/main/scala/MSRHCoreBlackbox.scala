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

import sys.process._

import chisel3._
import chisel3.util._
import chisel3.experimental.{IntParam, StringParam}

import scala.collection.mutable.{ListBuffer}

import freechips.rocketchip.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.diplomaticobjectmodel.logicaltree.{LogicalModuleTree, LogicalTreeNode, RocketLogicalTreeNode, ICacheLogicalTreeNode}
import freechips.rocketchip.rocket._
import freechips.rocketchip.subsystem.{RocketCrossingParams}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.util._
import freechips.rocketchip.tile._
import freechips.rocketchip.amba.axi4._

class msrh_tile_wrapper(
  xLen: Int
)
  extends BlackBox
  with HasBlackBoxResource
{
  val io = IO(new Bundle {
    val i_clk = Input(Clock())
    val i_reset_n = Input(Bool())

    val o_ic_req_valid   = Output(Bool())
    val o_ic_req_cmd     = Output(UInt(4.W))
    val o_ic_req_addr    = Output(UInt(39.W))
    val o_ic_req_tag     = Output(UInt(4.W))
    val o_ic_req_data    = Output(UInt(256.W))
    val o_ic_req_byte_en = Output(UInt((256/8).W))
    val i_ic_req_ready   = Input(Bool())

    val i_ic_resp_valid = Input(Bool())
    val i_ic_resp_tag   = Input(UInt(4.W))
    val i_ic_resp_data  = Input(UInt(256.W))
    val o_ic_resp_ready = Output(Bool())

    // L2 request from L1D
    val o_l1d_req_valid   = Output(Bool())
    val o_l1d_req_cmd     = Output(UInt(4.W))
    val o_l1d_req_addr    = Output(UInt(39.W))
    val o_l1d_req_tag     = Output(UInt(4.W))
    val o_l1d_req_data    = Output(UInt(256.W))
    val o_l1d_req_byte_en = Output(UInt((256/8).W)
    val i_l1d_req_ready   = Input(Bool())

    val i_l1d_resp_valid = Input(Bool())
    val i_l1d_resp_tag   = Input(UInt(4.W))
    val i_l1d_resp_data  = Input(UInt(256.W))
    val o_l1d_resp_ready = Output(Bool())

    // PTW interconnection
    val o_ptw_req_valid   = Output(Bool())
    val o_ptw_req_cmd     = Output(UInt(4.W))
    val o_ptw_req_addr    = Output(UInt(39.W))
    val o_ptw_req_tag     = Output(UInt(4.W))
    val o_ptw_req_data    = Output(UInt(256.W))
    val o_ptw_req_byte_en = Output(UInt((256/8).W)
    val i_ptw_req_ready   = Input(Bool())

    val i_ptw_resp_valid = Input(Bool())
    val i_ptw_resp_tag   = Input(UInt(4.W))
    val i_ptw_resp_data  = Input(UInt(256.W))
    val o_ptw_resp_ready = Output(Bool())
    })

  // pre-process the verilog to remove "includes" and combine into one file
  val make = "make -C generators/msrh/src/main/resources/vsrc default "
  val proc = make
  require (proc.! == 0, "Failed to run preprocessing step")

  // add wrapper/blackbox after it is pre-processed
  addResource("/vsrc/MSRHCoreBlackbox.preprocessed.sv")
}
