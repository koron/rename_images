#!/usr/local/bin/perl

use utf8;
use strict;
use warnings;

use Image::ExifTool;
use Image::EXIF::DateTime::Parser;
use File::Next;
use File::Basename;
use File::Copy;

my $DRYRUN = 1;

my $EXIFTOOL = Image::ExifTool->new();
my %VIRTUAL_FS;

my @targets;
for (my $i = 0; $i < scalar(@ARGV); ++$i) {
    if ('--run' eq $ARGV[$i]) {
        $DRYRUN = 0;
    } else {
        push @targets, $ARGV[$i];
    }
}
if (scalar(@targets) <= 0) {
    print "USAGE: $0 [--run] {TARGETS...}\n";
    exit;
}

foreach my $target (@targets) {
    if (-d $target) {
        my $files = File::Next::files($target);
        while (defined(my $file = $files->())) {
            &rename_image($file);
        }
    } else {
        &rename_image($target);
    }
}

sub rename_image
{
    my ($filename) = @_;

    my ($name, $dir) = fileparse($filename);
    if ($name !~ m/\.(jpg|jpeg)$/i) {
        print "DEBUG:skip non JPEG: $filename\n";
        return;
    } elsif ($name =~ m/^IMG_\d+_\d+(_\d+)?\./) {
        print "DEBUG:skip regulated name: $filename\n";
        return;
    }

    # Determine formatted time string.
    my $info = $EXIFTOOL->ImageInfo($filename);
    my $datetime = &get_datetime($info, $filename);
    if (not defined $datetime) {
        print "ERROR:Can't obtain datetime: $filename\n";
        return;
    }
    my $timestr = &format_time($datetime);

    # Determine regulated filename.
    my $regulated = &get_regulated_name($dir, $timestr);
    if (not defined $regulated) {
        print "ERROR:undetermined output: $filename\n"
    }

    my $path = $dir.$regulated;
    print "DEBUG:rename_image: $filename -> $regulated\n";
    if ($DRYRUN != 0) {
        $VIRTUAL_FS{$dir}->{$regulated} = 1;
    } else {
        if (-e $path) {
            print "ERROR:already exists: $path\n";
        } else {
            move($filename, $path);
        }
    }
}

sub get_datetime
{
    my ($info, $filename) = @_;

    my $exifTime;
    if (exists $info->{DateTimeOriginal}) {
        $exifTime = $info->{DateTimeOriginal};
    } elsif (exists $info->{CreateDate}) {
        $exifTime = $info->{CreateDate};
    } elsif (exists $info->{ModifyDate}) {
        $exifTime = $info->{ModifyDate};
    }
    if (defined $exifTime) {
        my $p = Image::EXIF::DateTime::Parser->new();
        my $retval = $p->parse($exifTime);
        if (defined $retval) {
            return $retval;
        } else {
            print "WARN: invalid EXIF time:$filename \"$exifTime\"\n";
        }
    }

    my $ctime = (stat($filename))[9];
    return $ctime;
}

sub format_time
{
    my ($value) = @_;
    my ($sec, $min, $hour, $mday, $mon, $year) = localtime($value);
    return sprintf("%04d%02d%02d_%02d%02d%02d", $year + 1900, $mon + 1, $mday,
        $hour, $min, $sec);
}

sub get_regulated_name
{
    my ($dir, $timestr) = @_;
    my $name = 'IMG_'.$timestr.'.JPG';
    return $name if not &is_exists_on_fs($dir, $name);
    for (my $i = 1; $i < 1000; ++$i) {
        $name = sprintf('IMG_%s_%03d.JPG', $timestr, $i);
        return $name if not &is_exists_on_fs($dir, $name);
    }
    return undef;
}

sub is_exists_on_fs
{
    my ($dir, $name) = @_;
    my $path = $dir.$name;
    if (-e $path) {
        return 1;
    } elsif ($DRYRUN != 0 and exists $VIRTUAL_FS{$dir}->{$name}) {
        return 1;
    } else {
        return 0;
    }
}
