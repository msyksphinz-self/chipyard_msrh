SRC_VLOG  =
SRC_VLOG += ./msrh/src/riscv_common_pkg.sv
SRC_VLOG += ./msrh/src/riscv64_pkg.sv
SRC_VLOG += ./msrh/src/msrh_standard_conf_pkg.sv
SRC_VLOG += ./msrh/src/msrh_csr_def.svh
# SRC_VLOG += ./msrh/src/decoder_inst_cat.sv
# SRC_VLOG += ./msrh/src/decoder_alu_ctrl.sv
# SRC_VLOG += ./msrh/src/decoder_lsu_ctrl.sv
# SRC_VLOG += ./msrh/src/decoder_bru_ctrl.sv
# SRC_VLOG += ./msrh/src/decoder_csu_ctrl.sv
# SRC_VLOG += ./msrh/src/decoder_reg.sv

SRC_VLOG += $(addprefix ./msrh/src/, $(shell cat msrh/src/filelist.f))
# SRC_VLOG += $(addprefix ./msrh/src/, $(shell cat msrh/verilator_sim/filelist.f))

default:
	cat $(SRC_VLOG) > MSRHCoreBlackbox.preprocessed.sv
	sed -i "/\`include/d" MSRHCoreBlackbox.preprocessed.sv
	ln -sf ../../../../generators/msrh/src/main/resources/vsrc/MSRHCoreBlackbox.preprocessed.sv \
		../../../../../../sims/verilator/generated-src/chipyard.TestHarness.MSRHConfig/
