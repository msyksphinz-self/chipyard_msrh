SRC_VLOG  =
SRC_VLOG += ./msrh/src/riscv_common_pkg.sv
SRC_VLOG += ./msrh/src/riscv64_pkg.sv
SRC_VLOG += ./msrh/src/mrh_pkg.sv
SRC_VLOG += ./msrh/src/l2_if.sv
SRC_VLOG += ./msrh/src/bit_oh_or.sv
SRC_VLOG += ./msrh/src/data_array.sv
SRC_VLOG += ./msrh/src/icache.sv
SRC_VLOG += ./msrh/src/tag_array.sv
SRC_VLOG += ./msrh/src/frontend.sv
SRC_VLOG += ./msrh/src/tlb.sv
SRC_VLOG += ./msrh/src/mrh_tile.sv
SRC_VLOG += ./msrh/src/mrh_tile_wrapper.sv

default:
	cat $(SRC_VLOG) > MSRHCoreBlackbox.preprocessed.sv
